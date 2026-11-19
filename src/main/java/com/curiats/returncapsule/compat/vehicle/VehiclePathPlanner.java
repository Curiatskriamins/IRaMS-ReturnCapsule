package com.curiats.returncapsule.compat.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small grid A* sized for a ground vehicle hull.
 *
 * <p>The reference implementation for this idea swaps the mob's dimensions for the hull's inside vanilla's
 * {@code WalkNodeEvaluator}, so the whole walking pathfinder plans for tank sized clearance. Our vehicle is not a
 * {@code Mob} and has no {@code PathNavigation}, so this class implements the same idea standalone: nodes on a
 * {@link #STEP} block grid, a node is passable only when the hull's bounding box fits there, and the search is bounded
 * so it can run on a server tick.</p>
 *
 * <p>Reactive ray probes answer "how far is the wall in front"; they cannot answer "which side of the lamp post is
 * open". A hull sized grid search can, which is what lets a vehicle thread the gaps between street furniture.</p>
 */
public final class VehiclePathPlanner {

    private static final double STEP = 2.0D;

    private static final int MAX_NODES = 256;

    private static final int MAX_CELLS = 48;

    private static final int MAX_STEP_UP = 2;

    private static final long REPATH_TICKS = 40L;

    /**
     * Global cap on searches per game tick. The reference implementation reports roughly 5 ms per vehicle sized
     * search and ships {@code pathSearchesPerTick = 0} (unlimited) by default, which lets a platoon of stale hulls
     * spend the whole tick inside A* - a per-tick cost floor no other config knob can lower. Two searches per tick
     * keeps the worst case at a few milliseconds.
     */
    private static final int SEARCHES_PER_TICK = 2;

    private static long budgetTick = Long.MIN_VALUE;

    private static int budgetUsed;

    private static final double GOAL_SLACK = 3.0D;

    private static final double LOOKAHEAD = 7.0D;

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
                // Out of slots this tick: keep following the route we already have, if any.
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

    private static List<Vec3> plan(Level level, Entity vehicle, Vec3 goal) {
        Vec3 start = vehicle.position();
        int startGx = grid(start.x);
        int startGz = grid(start.z);
        int goalGx = grid(goal.x);
        int goalGz = grid(goal.z);
        if (Math.abs(goalGx - startGx) + Math.abs(goalGz - startGz) > MAX_CELLS) {
            return List.of();
        }
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Double> heights = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Node startNode = new Node(startGx, startGz, start.y, 0.0D, heuristic(startGx, startGz, goalGx, goalGz), null);
        open.add(startNode);
        best.put(key(startGx, startGz), 0.0D);
        heights.put(key(startGx, startGz), start.y);
        Node closest = startNode;
        Node reached = null;
        int expanded = 0;
        while (!open.isEmpty() && expanded < MAX_NODES) {
            Node node = open.poll();
            Double known = best.get(key(node.gx, node.gz));
            if (known != null && node.g > known + 1.0E-4D) {
                continue;
            }
            expanded++;
            if (Math.abs(node.gx - goalGx) + Math.abs(node.gz - goalGz) <= 1) {
                reached = node;
                break;
            }
            if (node.h < closest.h) {
                closest = node;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int gx = node.gx + dx;
                    int gz = node.gz + dz;
                    if (Math.abs(gx - startGx) > MAX_CELLS || Math.abs(gz - startGz) > MAX_CELLS) {
                        continue;
                    }
                    if (dx != 0 && dz != 0) {
                        // No corner cutting: both orthogonal cells have to fit the hull too.
                        if (height(level, vehicle, node.gx + dx, node.gz, heights) == null
                                || height(level, vehicle, node.gx, node.gz + dz, heights) == null) {
                            continue;
                        }
                    }
                    Double y = height(level, vehicle, gx, gz, heights);
                    if (y == null || Math.abs(y - node.y) > MAX_STEP_UP) {
                        continue;
                    }
                    double stepCost = Math.sqrt(dx * dx + dz * dz) * STEP + Math.abs(y - node.y) * 0.8D;
                    double g = node.g + stepCost;
                    long key = key(gx, gz);
                    Double previous = best.get(key);
                    if (previous != null && previous <= g) {
                        continue;
                    }
                    best.put(key, g);
                    open.add(new Node(gx, gz, y, g, heuristic(gx, gz, goalGx, goalGz), node));
                }
            }
        }
        Node end = reached != null ? reached : closest;
        List<Vec3> points = new ArrayList<>();
        for (Node cursor = end; cursor != null; cursor = cursor.parent) {
            points.add(0, new Vec3(cursor.gx * STEP, cursor.y, cursor.gz * STEP));
        }
        while (points.size() > 1 && points.get(0).distanceTo(start) < STEP) {
            points.remove(0);
        }
        return points;
    }

    private static Double height(Level level, Entity vehicle, int gx, int gz, Map<Long, Double> cache) {
        long key = key(gx, gz);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Double result = probe(level, vehicle, gx * STEP, gz * STEP);
        cache.put(key, result);
        return result;
    }

    /**
     * @return the hull's resting Y at that column, or {@code null} when the hull does not fit there
     */
    private static Double probe(Level level, Entity vehicle, double x, double z) {
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
        return level.noCollision(vehicle, box) ? (double) y : null;
    }

    private static double heuristic(int gx, int gz, int goalGx, int goalGz) {
        double dx = (gx - goalGx) * STEP;
        double dz = (gz - goalGz) * STEP;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int grid(double coordinate) {
        return (int) Math.round(coordinate / STEP);
    }

    private static long key(int gx, int gz) {
        return ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
    }

    private static final class Node {
        private final int gx;
        private final int gz;
        private final double y;
        private final double g;
        private final double h;
        private final Node parent;

        private Node(int gx, int gz, double y, double g, double h, Node parent) {
            this.gx = gx;
            this.gz = gz;
            this.y = y;
            this.g = g;
            this.h = h;
            this.parent = parent;
        }

        private double f() {
            return this.g + this.h;
        }
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
            return this.points.get(Math.min(index, this.points.size() - 1));
        }
    }
}
