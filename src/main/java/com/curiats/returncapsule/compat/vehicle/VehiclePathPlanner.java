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

public final class VehiclePathPlanner {

    private static final double STEP = 2.0D;
    private static final int MAX_NODES = 128;
    private static final int MAX_SMOOTH_CELLS = 24;
    private static final int MAX_CELLS = 48;
    private static final int SPAN = MAX_CELLS * 2 + 1;
    private static final int AREA = SPAN * SPAN;
    private static final long REPATH_TICKS = 100L;
    /** Searches are drip fed: one token every this many ticks, so a convoy cannot stack searches back to back. */
    private static final int SEARCH_INTERVAL_TICKS = 4;

    private static final int SEARCH_TOKENS_MAX = 2;

    private static long tokenTick = Long.MIN_VALUE;

    private static int tokens;
    private static final double GOAL_SLACK = 8.0D;
    private static final double LOOKAHEAD = 7.0D;
    private static final double OFF_PATH = 8.0D;

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
    private static int originX;
    private static int originZ;

    private static final Map<Integer, Cached> CACHE = new ConcurrentHashMap<>();

    private VehiclePathPlanner() {
    }

    public static Vec3 route(Entity vehicle, Vec3 goal) {
        if (vehicle.level().isClientSide()) {
            return null;
        }
        long now = vehicle.level().getGameTime();
        int id = vehicle.getId();
        if (now % 2048L == 0L) {
            CACHE.entrySet().removeIf(entry -> now - entry.getValue().stamp() > 6000L);
        }
        Cached cached = CACHE.get(id);
        long period = REPATH_TICKS + Math.floorMod(id, 20);
        if (cached != null && cached.points().isEmpty() && now - cached.stamp() <= period) {
            return null;
        }
        boolean stale = cached == null
                || now - cached.stamp() > period
                || cached.goal().distanceToSqr(goal) > GOAL_SLACK * GOAL_SLACK
                || cached.offPath(vehicle.position());
        if (stale) {
            if (tokenTick == Long.MIN_VALUE) {
                tokenTick = now;
                tokens = SEARCH_TOKENS_MAX;
            }
            while (tokenTick <= now) {
                tokenTick += SEARCH_INTERVAL_TICKS;
                tokens = Math.min(SEARCH_TOKENS_MAX, tokens + 1);
            }
            if (tokens > 0) {
                tokens--;
                    List<Vec3> points = plan(vehicle.level(), vehicle, goal);
                cached = new Cached(points, goal, now);
                CACHE.put(id, cached);
            } else if (cached == null) {
                return null;
            }
        }
        if (cached == null) {
            return null;
        }
        return cached.waypoint(vehicle.position());
    }

    public static void forget(int entityId) {
        CACHE.remove(entityId);
    }

    private static List<Vec3> plan(Level level, Entity vehicle, Vec3 goal) {
        Vec3 start = vehicle.position();
        int startGx = grid(start.x);
        int startGz = grid(start.z);
        int goalGx = grid(goal.x);
        int goalGz = grid(goal.z);
        if (Math.abs(goalGx - startGx) + Math.abs(goalGz - startGz) > MAX_CELLS) {
            return List.of();
        }
        if (start.distanceToSqr(goal) < 9.0D) {
            return List.of();
        }
        originX = startGx;
        originZ = startGz;
        Arrays.fill(STATE, (byte) 0);
        Arrays.fill(HEIGHT_KNOWN, (byte) 0);
        Arrays.fill(G_SCORE, Float.POSITIVE_INFINITY);
        Arrays.fill(CELL_Y, Float.NaN);
        Arrays.fill(HEAP_POSITION, -1);
        heapSize = 0;

        double stepUp = isTracked(vehicle) ? 2.0D : 1.0D;
        int startCell = cell(0, 0);
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
                    int localNextX = nextX - startGx;
                    int localNextZ = nextZ - startGz;
                    Double nextY = heightAt(level, vehicle, localNextX, localNextZ, stepUp);
                    if (nextY == null || Math.abs(nextY - nodeY) > stepUp) {
                        continue;
                    }
                    if (dx != 0 && dz != 0) {
                        int sideAX = nextX - startGx;
                        int sideAZ = nextZ - dz - startGz;
                        int sideBX = nextX - dx - startGx;
                        int sideBZ = nextZ - startGz;
                        if (Math.abs(sideAX) > MAX_CELLS || Math.abs(sideAZ) > MAX_CELLS
                                || Math.abs(sideBX) > MAX_CELLS || Math.abs(sideBZ) > MAX_CELLS) {
                            continue;
                        }
                        Double sideA = heightAt(level, vehicle, sideAX, sideAZ, stepUp);
                        Double sideB = heightAt(level, vehicle, sideBX, sideBZ, stepUp);
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
                    double f = tentative + heuristic(localNextX, localNextZ,
                            goalGx - startGx, goalGz - startGz);
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
        if (cells.size() <= 1) {
            return List.of();
        }
        cells = smooth(level, vehicle, cells, startGx, startGz, stepUp);
        List<Vec3> points = new ArrayList<>(cells.size());
        for (int[] cell : cells) {
            int index = cell(cell[0], cell[1]);
            points.add(new Vec3((startGx + cell[0]) * STEP, CELL_Y[index], (startGz + cell[1]) * STEP));
        }
        while (points.size() > 1 && points.get(0).distanceTo(start) < STEP) {
            points.remove(0);
        }
        return points;
    }

    private static List<int[]> smooth(Level level, Entity vehicle, List<int[]> cells,
            int startGx, int startGz, double stepUp) {
        if (cells.size() <= 2) {
            return cells;
        }
        List<int[]> out = new ArrayList<>();
        int index = 0;
        out.add(cells.get(0));
        while (index < cells.size() - 1) {
            int best = Math.min(cells.size() - 1, index + MAX_SMOOTH_CELLS);
            while (best > index + 1 && !segmentClear(level, vehicle, cells.get(index), cells.get(best),
                    startGx, startGz, stepUp)) {
                best--;
            }
            out.add(cells.get(best));
            index = best;
        }
        return out;
    }

    private static boolean segmentClear(Level level, Entity vehicle, int[] from, int[] to,
            int startGx, int startGz, double stepUp) {
        double ax = (startGx + from[0]) * STEP;
        double az = (startGz + from[1]) * STEP;
        double bx = (startGx + to[0]) * STEP;
        double bz = (startGz + to[1]) * STEP;
        double dx = bx - ax;
        double dz = bz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        int samples = Math.max(1, (int) Math.ceil(length));
        AABB base = vehicle.getBoundingBox().deflate(0.05D);
        for (int sample = 1; sample <= samples; sample++) {
            double t = (double) sample / samples;
            double x = ax + dx * t;
            double z = az + dz * t;
            AABB moved = base.move(x - vehicle.getX(), 0.0D, z - vehicle.getZ());
            if (VehicleTerrain.boxBlocked(level, moved, stepUp)) {
                return false;
            }
        }
        return true;
    }

    private static Double heightAt(Level level, Entity vehicle, int localX, int localZ, double stepUp) {
        if (Math.abs(localX) > MAX_CELLS || Math.abs(localZ) > MAX_CELLS) {
            return null;
        }
        int index = cell(localX, localZ);
        if (HEIGHT_KNOWN[index] != 0) {
            return Float.isNaN(CELL_Y[index]) ? null : Double.valueOf(CELL_Y[index]);
        }
        HEIGHT_KNOWN[index] = 1;
        Double height = probe(level, vehicle, localX, localZ, stepUp);
        CELL_Y[index] = height == null ? Float.NaN : height.floatValue();
        return height;
    }

    private static Double probe(Level level, Entity vehicle, int localX, int localZ, double stepUp) {
        double x = (originX + localX) * STEP;
        double z = (originZ + localZ) * STEP;
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(x, 0.0D, z));
        int y = surface.getY();
        BlockPos ground = BlockPos.containing(x, y - 1, z);
        if (level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
                || !level.getBlockState(ground).getFluidState().isEmpty()) {
            return null;
        }
        if (!level.getFluidState(surface).isEmpty() || !level.getFluidState(surface.above()).isEmpty()) {
            return null;
        }
        if (y < level.getMinBuildHeight() + 1 || y > level.getMaxBuildHeight() - 4) {
            return null;
        }
        // Two blocks of headroom instead of a full hull box scan per node: the planner only walks on the surface, and
        // the per node box scan was the single biggest cost of one search.
        BlockPos head1 = surface;
        BlockPos head2 = surface.above();
        if (!level.getBlockState(head1).getCollisionShape(level, head1).isEmpty()
                || !level.getBlockState(head2).getCollisionShape(level, head2).isEmpty()) {
            return null;
        }
        return (double) y;
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
            return best > OFF_PATH * OFF_PATH;
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
            int index = nearest;
            while (index + 1 < this.points.size() && this.points.get(index).distanceToSqr(position) < 4.0D) {
                index++;
            }
            double remaining = LOOKAHEAD;
            while (index + 1 < this.points.size() && remaining > 0.0D) {
                remaining -= this.points.get(index).distanceTo(this.points.get(index + 1));
                index++;
            }
            Vec3 chosen = this.points.get(index);
            if (chosen.distanceToSqr(position) < 4.0D) {
                return null;
            }
            return chosen;
        }
    }
}
