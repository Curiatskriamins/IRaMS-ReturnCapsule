package com.curiats.returncapsule.compat.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small grid A* sized for a ground vehicle hull.
 *
 * <p>The search mirrors the shape of Baritone's pathfinder, scaled down to a hull: the grid is bounded, so the
 * bookkeeping lives in flat arrays indexed by cell (no {@code HashMap<Long, Double>} and no boxing), the open set is a
 * binary heap over cell indices with a parallel cost array, and a cell is expanded at most once. With a 97 x 97 grid a
 * four hundred node search allocates nothing at all, which matters when a platoon of hulls all ask for a route on the
 * same tick.</p>
 *
 * <p>Nodes are passable only where the hull's bounding box fits, using the same step height as the driver so the two
 * never disagree about a kerb. Diagonal moves may not cut corners, and the resulting grid path is string pulled so the
 * hull does not zigzag between cells.</p>
 */
public final class VehiclePathPlanner {

    private static final double STEP = 2.0D;

    private static final int MAX_NODES = 512;

    private static final int MAX_CELLS = 48;

    private static final int SPAN = MAX_CELLS * 2 + 1;

    private static final int AREA = SPAN * SPAN;

    private static final double MAX_STEP_UP = 2.0D;

    private static final long REPATH_TICKS = 40L;

    /** Global cap on searches per game tick, so a convoy cannot spend the whole tick inside A*. */
    private static final int SEARCHES_PER_TICK = 2;

    private static final double GOAL_SLACK = 3.0D;

    private static final double LOOKAHEAD = 7.0D;

    // ---- scratch grid, reused between searches (the server thread runs one at a time) -------------------------
    private static final float[] G_SCORE = new float[AREA];
    private static final float[] CELL_Y = new float[AREA];
    private static final int[] PARENT = new int[AREA];
    private static final byte[] STATE = new byte[AREA];
    private static final byte[] HEIGHT_KNOWN = new byte[AREA];
    private static final int[] HEAP_CELL = new int[AREA];
    private static final double[] HEAP_KEY = new double[AREA];
    private static final int[] HEAP_POSITION = new int[AREA];

    private static int heapSize;

    private static long budgetTick = Long.MIN_VALUE;

    private static int budgetUsed;

    private static final Map<Integer, Cached> CACHE = new ConcurrentHashMap<>();

    private VehiclePathPlanner() {
    }

    /**
     * Returns the waypoint the hull should steer at right now, or {@code null} when no route could be planned (the
     * caller then falls back to its reactive behaviour).
     */
    public static Vec3 route(Entity vehicle, Vec3 goal) {
        if (vehicle.level().isClientSide()) {
            return null;
        }
        long now = vehicle.level().getGameTime();
        Cached cached = CACHE.get(vehicle.getId());
        // Stagger the refresh by entity id so a convoy does not all go stale on the same tick.
        long period = REPATH_TICKS + Math.floorMod(vehicle.getId(), 20);
        boolean stale = cached == null
                || now - cached.stamp() > period
                || cached.goal().distanceToSqr(goal) > GOAL_SLACK * GOAL_SLACK
                || cached.offPath(vehicle.position());
        if (stale) {
            if (budgetTick != now) {
                budgetTick = now;
                budgetUsed = 0;
            }
            if (budgetUsed >= SEARCHES_PER_TICK) {
                return cached == null ? null : cached.waypoint(vehicle.position());
            }
            budgetUsed++;
            List<Vec3> points = plan(vehicle.level(), vehicle, goal);
            cached = new Cached(points, goal, now);
            CACHE.put(vehicle.getId(), cached);
        }
        return cached.waypoint(vehicle.position());
    }

    public static void forget(int entityId) {
        CACHE.remove(entityId);
    }

    // ------------------------------------------------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------------------------------------------------

    private static List<Vec3> plan(Level level, Entity vehicle, Vec3 goal) {
        Vec3 start = vehicle.position();
        int startGx = grid(start.x);
        int startGz = grid(start.z);
        int goalGx = grid(goal.x);
        int goalGz = grid(goal.z);
        if (Math.abs(goalGx - startGx) + Math.abs(goalGz - startGz) > MAX_CELLS) {
            return List.of();
        }
        Arrays.fill(STATE, (byte) 0);
        Arrays.fill(HEIGHT_KNOWN, (byte) 0);
        Arrays.fill(G_SCORE, Float.POSITIVE_INFINITY);
        Arrays.fill(HEAP_POSITION, -1);
        heapSize = 0;

        double stepUp = isTracked(vehicle) ? 2.0D : 1.0D;
        int startCell = cell(startGx - startGx, startGz - startGz);
        CELL_Y[startCell] = (float) start.y;
        HEIGHT_KNOWN[startCell] = 1;
        G_SCORE[startCell] = 0.0F;
        PARENT[startCell] = -1;
        STATE[startCell] = 1;
        push(startCell, heuristic(0, 0, goalGx - startGx, goalGz - startGz));

        int closest = startCell;
        float closestH = (float) heuristic(0, 0, goalGx - startGx, goalGz - startGz);
        int reached = -1;
        int expanded = 0;
        while (heapSize > 0 && expanded < MAX_NODES) {
            int cell = pop();
            if (STATE[cell] == 2) {
                continue;
            }
            STATE[cell] = 2;
            expanded++;
            int localX = cell % SPAN - MAX_CELLS;
            int localZ = cell / SPAN - MAX_CELLS;
            int absoluteX = startGx + localX;
            int absoluteZ = startGz + localZ;
            if (Math.abs(absoluteX - goalGx) + Math.abs(absoluteZ - goalGz) <= 1) {
                reached = cell;
                break;
            }
            float h = (float) heuristic(localX, localZ, goalGx - startGx, goalGz - startGz);
            if (h < closestH) {
                closestH = h;
                closest = cell;
            }
            float nodeY = CELL_Y[cell];
            float nodeG = G_SCORE[cell];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nextX = absoluteX + dx;
                    int nextZ = absoluteZ + dz;
                    if (Math.abs(nextX - startGx) > MAX_CELLS || Math.abs(nextZ - startGz) > MAX_CELLS) {
                        continue;
                    }
                    int nextCell = cell(nextX - startGx, nextZ - startGz);
                    if (STATE[nextCell] == 2) {
                        continue;
                    }
                    Double nextY = heightAt(level, vehicle, nextX - startGx, nextZ - startGz, stepUp);
                    if (nextY == null || Math.abs(nextY - nodeY) > MAX_STEP_UP) {
                        continue;
                    }
                    if (dx != 0 && dz != 0) {
                        // No corner cutting: both orthogonal cells have to fit the hull too.
                        Double sideA = heightAt(level, vehicle, nextX - startGx, nextZ - dz - startGz, stepUp);
                        Double sideB = heightAt(level, vehicle, nextX - dx - startGx, nextZ - startGz, stepUp);
                        if (sideA == null || sideB == null) {
                            continue;
                        }
                    }
                    float tentative = nodeG + (float) (Math.sqrt(dx * dx + dz * dz) * STEP
                            + Math.abs(nextY - nodeY) * 0.8D);
                    if (tentative >= G_SCORE[nextCell]) {
                        continue;
                    }
                    G_SCORE[nextCell] = tentative;
                    CELL_Y[nextCell] = nextY.floatValue();
                    PARENT[nextCell] = cell;
                    double f = tentative + heuristic(nextX - startGx, nextZ - startGz, goalGx - startGx, goalGz - startGz);
                    if (STATE[nextCell] == 1) {
                        decrease(nextCell, f);
                    } else {
                        STATE[nextCell] = 1;
                        push(nextCell, f);
                    }
                }
            }
        }

        int end = reached >= 0 ? reached : closest;
        List<int[]> cells = new ArrayList<>();
        for (int cursor = end; cursor != -1; cursor = PARENT[cursor]) {
            cells.add(0, new int[]{cursor % SPAN - MAX_CELLS, cursor / SPAN - MAX_CELLS});
        }
        cells = smooth(cells);
        List<Vec3> points = new ArrayList<>(cells.size());
        for (int[] cell : cells) {
            int cellIndex = cell(cell[0], cell[1]);
            points.add(new Vec3((startGx + cell[0]) * STEP, CELL_Y[cellIndex], (startGz + cell[1]) * STEP));
        }
        while (points.size() > 1 && points.get(0).distanceTo(start) < STEP) {
            points.remove(0);
        }
        return points;
    }

    /**
     * String pulling: keep only the corners the hull can actually see across, so the follow up does not zigzag cell
     * to cell. Cells are compared on the same validity data the search filled in.
     */
    private static List<int[]> smooth(List<int[]> cells) {
        if (cells.size() <= 2) {
            return cells;
        }
        List<int[]> out = new ArrayList<>();
        int index = 0;
        out.add(cells.get(0));
        while (index < cells.size() - 1) {
            int best = cells.size() - 1;
            while (best > index + 1 && !clearLine(cells, index, best)) {
                best--;
            }
            out.add(cells.get(best));
            index = best;
        }
        return out;
    }

    private static boolean clearLine(List<int[]> cells, int fromIndex, int toIndex) {
        int[] from = cells.get(fromIndex);
        int[] to = cells.get(toIndex);
        int dx = Math.abs(to[0] - from[0]);
        int dz = Math.abs(to[1] - from[1]);
        int sx = Integer.signum(to[0] - from[0]);
        int sz = Integer.signum(to[1] - from[1]);
        int error = dx - dz;
        int x = from[0];
        int z = from[1];
        float previousY = CELL_Y[cell(x, z)];
        while (x != to[0] || z != to[1]) {
            int doubled = 2 * error;
            if (doubled > -dz) {
                error -= dz;
                x += sx;
            }
            if (doubled < dx) {
                error += dx;
                z += sz;
            }
            int index = cell(x, z);
            if (HEIGHT_KNOWN[index] == 0 || CELL_Y[index] < 0.0F) {
                return false;
            }
            float y = CELL_Y[index];
            if (Math.abs(y - previousY) > MAX_STEP_UP) {
                return false;
            }
            previousY = y;
        }
        return true;
    }

    private static Double heightAt(Level level, Entity vehicle, int localX, int localZ, double stepUp) {
        int index = cell(localX, localZ);
        if (HEIGHT_KNOWN[index] != 0) {
            return CELL_Y[index] >= 0.0F ? Double.valueOf(CELL_Y[index]) : null;
        }
        HEIGHT_KNOWN[index] = 1;
        Double height = probe(level, vehicle, localX, localZ, stepUp);
        CELL_Y[index] = height == null ? -1.0F : height.floatValue();
        return height;
    }

    /**
     * @return the hull's resting Y at that column, or {@code null} when the hull does not fit there
     */
    private static Double probe(Level level, Entity vehicle, int localX, int localZ, double stepUp) {
        double x = localX * STEP;
        double z = localZ * STEP;
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos((int) Math.floor(x), 0, (int) Math.floor(z)));
        int y = surface.getY();
        BlockPos ground = new BlockPos((int) Math.floor(x), y - 1, (int) Math.floor(z));
        if (level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
                || !level.getBlockState(ground).getFluidState().isEmpty()) {
            return null;
        }
        if (!level.getFluidState(surface).isEmpty() || !level.getFluidState(surface.above()).isEmpty()) {
            return null;
        }
        AABB box = vehicle.getBoundingBox().move(x - vehicle.getX(), y - vehicle.getY(), z - vehicle.getZ());
        box = box.deflate(0.05D);
        return VehicleTerrain.boxBlocked(level, box, stepUp) ? null : (double) y;
    }

    private static double heuristic(int localX, int localZ, int goalLocalX, int goalLocalZ) {
        double dx = (localX - goalLocalX) * STEP;
        double dz = (localZ - goalLocalZ) * STEP;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int grid(double coordinate) {
        return (int) Math.round(coordinate / STEP);
    }

    private static int cell(int localX, int localZ) {
        return (localX + MAX_CELLS) + (localZ + MAX_CELLS) * SPAN;
    }

    private static void push(int cell, double key) {
        heapSize++;
        int index = heapSize;
        while (index > 1) {
            int parent = index >>> 1;
            if (HEAP_KEY[parent] <= key) {
                break;
            }
            HEAP_CELL[index] = HEAP_CELL[parent];
            HEAP_KEY[index] = HEAP_KEY[parent];
            HEAP_POSITION[HEAP_CELL[index]] = index;
            index = parent;
        }
        HEAP_CELL[index] = cell;
        HEAP_KEY[index] = key;
        HEAP_POSITION[cell] = index;
    }

    private static void decrease(int cell, double key) {
        int index = HEAP_POSITION[cell];
        if (index <= 0) {
            push(cell, key);
            return;
        }
        while (index > 1) {
            int parent = index >>> 1;
            if (HEAP_KEY[parent] <= key) {
                break;
            }
            HEAP_CELL[index] = HEAP_CELL[parent];
            HEAP_KEY[index] = HEAP_KEY[parent];
            HEAP_POSITION[HEAP_CELL[index]] = index;
            index = parent;
        }
        HEAP_CELL[index] = cell;
        HEAP_KEY[index] = key;
        HEAP_POSITION[cell] = index;
    }

    private static int pop() {
        int result = HEAP_CELL[1];
        HEAP_POSITION[result] = -1;
        int lastCell = HEAP_CELL[heapSize];
        double lastKey = HEAP_KEY[heapSize];
        heapSize--;
        if (heapSize == 0) {
            return result;
        }
        int index = 1;
        while (true) {
            int child = index << 1;
            if (child > heapSize) {
                break;
            }
            if (child + 1 <= heapSize && HEAP_KEY[child + 1] < HEAP_KEY[child]) {
                child++;
            }
            if (HEAP_KEY[child] >= lastKey) {
                break;
            }
            HEAP_CELL[index] = HEAP_CELL[child];
            HEAP_KEY[index] = HEAP_KEY[child];
            HEAP_POSITION[HEAP_CELL[index]] = index;
            index = child;
        }
        HEAP_CELL[index] = lastCell;
        HEAP_KEY[index] = lastKey;
        HEAP_POSITION[lastCell] = index;
        return result;
    }

    private static boolean isTracked(Entity vehicle) {
        for (Class<?> type = vehicle.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().contains("Tracked")) {
                return true;
            }
        }
        return false;
    }

    private record Cached(List<Vec3> points, Vec3 goal, long stamp) {

        private boolean offPath(Vec3 position) {
            if (this.points.isEmpty()) {
                return true;
            }
            double best = Double.MAX_VALUE;
            for (Vec3 point : this.points) {
                best = Math.min(best, point.distanceToSqr(position));
            }
            return best > 100.0D;
        }

        private Vec3 waypoint(Vec3 position) {
            if (this.points.isEmpty()) {
                return null;
            }
            int nearest = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < this.points.size(); i++) {
                double distance = this.points.get(i).distanceToSqr(position);
                if (distance < best) {
                    best = distance;
                    nearest = i;
                }
            }
            double remaining = LOOKAHEAD;
            int index = nearest;
            while (index + 1 < this.points.size() && remaining > 0.0D) {
                remaining -= this.points.get(index).distanceTo(this.points.get(index + 1));
                index++;
            }
            return this.points.get(index);
        }
    }
}
