package com.curiats.returncapsule.compat.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cached support-height slice around each hull.
 *
 * <p>A plain "does this block have a collision" flag cannot tell a full block from a trapdoor plate, and using the same
 * flag for both obstacles and floors is what made a stair and trapdoor track bed misbehave: the plate counted as floor
 * while the physics only offered centimetres of support. Each column therefore stores the height of its highest
 * collision surface relative to the hull's feet, in sixteenths of a block. That single number answers both questions:
 * a column is an obstacle when its surface is higher than the hull can climb, and it is floor when its surface is near
 * the feet. A trapdoor bridge works because while the hull stands on it, its feet are at the plate height, so the
 * column measures zero.</p>
 */
public final class HullTerrain {

    private static final int RADIUS = 12;

    private static final int SIDE = RADIUS * 2 + 1;

    /** Layers relative to the hull feet: one below, then the hull's own height band. */
    private static final int LAYERS = 7;

    private static final int UNITS = 16;

    private static final short NO_TOP = Short.MIN_VALUE;

    /** A column whose topmost cached layer is solid continues above the slice; treat it as tall. */
    private static final short TALL = (short) (LAYERS * UNITS + UNITS);

    private static final int REFRESH_TICKS = 8;

    private static final int MIN_DELAY = 8;

    private static final int MAX_DELAY = 80;

    private record Cache(int centerX, int centerZ, int footY, short[] top, long stamp) {
    }

    private static final Map<Integer, Cache> CACHES = new ConcurrentHashMap<>();

    private static final Map<Integer, Long> NEXT_REFRESH = new ConcurrentHashMap<>();

    private static final Map<Integer, Integer> REFRESH_DELAY = new ConcurrentHashMap<>();

    private HullTerrain() {
    }

    public static void refresh(Entity vehicle) {
        Level level = vehicle.level();
        BlockPos centre = vehicle.blockPosition();
        int footY = Mth.floor(vehicle.getBoundingBox().minY - 0.01D);
        long now = level.getGameTime();
        Cache cache = CACHES.get(vehicle.getId());
        if (now < NEXT_REFRESH.getOrDefault(vehicle.getId(), Long.MIN_VALUE)) {
            return;
        }
        int delay = REFRESH_DELAY.getOrDefault(vehicle.getId(), REFRESH_TICKS);
        if (cache != null && cache.centerX() == centre.getX() && cache.centerZ() == centre.getZ()
                && Math.abs(cache.footY() - footY) <= 1 && now - cache.stamp() < delay) {
            return;
        }
        long started = System.nanoTime();
        short[] top;
        if (cache != null && Math.abs(cache.footY() - footY) <= 1
                && Math.abs(centre.getX() - cache.centerX()) <= RADIUS
                && Math.abs(centre.getZ() - cache.centerZ()) <= RADIUS) {
            top = shifted(level, cache, centre.getX(), centre.getZ());
        } else {
            top = new short[SIDE * SIDE];
            fill(level, top, centre.getX(), centre.getZ(), footY);
        }
        CACHES.put(vehicle.getId(), new Cache(centre.getX(), centre.getZ(), footY, top, now));
        double millis = (System.nanoTime() - started) / 1_000_000.0D;
        if (millis > 1.2D) {
            delay = Math.min(MAX_DELAY, Math.max(MIN_DELAY, delay * 2));
        } else {
            delay = Math.max(REFRESH_TICKS, delay / 2);
        }
        REFRESH_DELAY.put(vehicle.getId(), delay);
        NEXT_REFRESH.put(vehicle.getId(), now + delay);
    }

    public static void forget(int vehicleId) {
        CACHES.remove(vehicleId);
        NEXT_REFRESH.remove(vehicleId);
        REFRESH_DELAY.remove(vehicleId);
    }

    /** True when a column's support surface is higher than the hull can climb. */
    public static boolean boxBlocked(Entity vehicle, AABB box, double stepHeight) {
        Cache cache = CACHES.get(vehicle.getId());
        if (cache == null) {
            return VehicleTerrain.boxBlocked(vehicle.level(), box, stepHeight);
        }
        int minX = Mth.floor(box.minX + 0.01D);
        int maxX = Mth.floor(box.maxX - 0.01D);
        int minZ = Mth.floor(box.minZ + 0.01D);
        int maxZ = Mth.floor(box.maxZ - 0.01D);
        int topY = Mth.floor(box.maxY - 0.01D);
        int ceilingUnits = (int) Math.ceil(stepHeight * UNITS);
        int hullTopUnits = (topY - cache.footY()) * UNITS;
        if (hullTopUnits < ceilingUnits) {
            return false;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                short relative = top(cache, x, z, vehicle.level());
                if (relative == NO_TOP) {
                    continue;
                }
                int units = relative;
                if (units > ceilingUnits && units < hullTopUnits + UNITS) {
                    return true;
                }
            }
        }
        return false;
    }

    public static double clearance(Entity vehicle, AABB box, Vec3 direction, double max, double stepHeight) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return max;
        }
        Vec3 unit = flat.normalize();
        for (double travelled = 1.0D; travelled <= max; travelled += 1.0D) {
            AABB moved = box.move(unit.x * travelled, 0.0D, unit.z * travelled);
            if (boxBlocked(vehicle, moved, stepHeight)) {
                return Math.max(0.0D, travelled - 1.0D);
            }
        }
        return max;
    }

    /** True when the support surface under the box sits close to the hull's feet (a real floor). */
    public static boolean groundUnder(Entity vehicle, AABB box, double maxDrop) {
        Cache cache = CACHES.get(vehicle.getId());
        if (cache == null) {
            return VehicleTerrain.groundUnder(vehicle.level(), box, maxDrop);
        }
        int dropUnits = (int) Math.ceil(maxDrop * UNITS);
        int aboveUnits = UNITS;
        double[][] samples = {
                {(box.minX + box.maxX) * 0.5D, (box.minZ + box.maxZ) * 0.5D},
                {box.minX + 0.3D, box.minZ + 0.3D},
                {box.maxX - 0.3D, box.minZ + 0.3D},
                {box.minX + 0.3D, box.maxZ - 0.3D},
                {box.maxX - 0.3D, box.maxZ - 0.3D}
        };
        for (double[] sample : samples) {
            int x = Mth.floor(sample[0]);
            int z = Mth.floor(sample[1]);
            short relative = top(cache, x, z, vehicle.level());
            if (relative == NO_TOP) {
                continue;
            }
            int units = relative;
            if (units >= -dropUnits && units <= aboveUnits) {
                return true;
            }
        }
        return false;
    }

    private static short[] shifted(Level level, Cache cache, int centerX, int centerZ) {
        short[] top = new short[SIDE * SIDE];
        int shiftX = centerX - cache.centerX();
        int shiftZ = centerZ - cache.centerZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int oldX = dx + shiftX;
                int oldZ = dz + shiftZ;
                if (Math.abs(oldX) <= RADIUS && Math.abs(oldZ) <= RADIUS) {
                    top[index(dx, dz)] = cache.top()[index(oldX, oldZ)];
                } else {
                    top[index(dx, dz)] = columnTop(level, pos, centerX + dx, centerZ + dz, cache.footY());
                }
            }
        }
        return top;
    }

    private static void fill(Level level, short[] top, int centerX, int centerZ, int footY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                top[index(dx, dz)] = columnTop(level, pos, centerX + dx, centerZ + dz, footY);
            }
        }
    }

    /** Height of the highest collision surface in the cached band, relative to the feet, in sixteenths. */
    private static short columnTop(Level level, BlockPos.MutableBlockPos pos, int x, int z, int footY) {
        for (int layer = LAYERS - 1; layer >= 0; layer--) {
            int y = footY - 1 + layer;
            pos.set(x, y, z);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                if (layer == LAYERS - 1) {
                    return TALL;
                }
                return (short) Math.round(((y + shape.max(net.minecraft.core.Direction.Axis.Y)) - footY) * UNITS);
            }
        }
        return NO_TOP;
    }

    private static short top(Cache cache, int x, int z, Level level) {
        int dx = x - cache.centerX();
        int dz = z - cache.centerZ();
        if (Math.abs(dx) <= RADIUS && Math.abs(dz) <= RADIUS) {
            return cache.top()[index(dx, dz)];
        }
        return columnTop(level, new BlockPos.MutableBlockPos(), x, z, cache.footY());
    }

    private static int index(int dx, int dz) {
        return (dx + RADIUS) * SIDE + (dz + RADIUS);
    }
}
