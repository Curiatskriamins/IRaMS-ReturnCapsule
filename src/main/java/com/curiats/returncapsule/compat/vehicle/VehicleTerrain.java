package com.curiats.returncapsule.compat.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Terrain queries for a vehicle hull, written to be cheap enough to run every tick.
 *
 * <p>Everything works on whole blocks. A block row whose top is at or below the hull's climb ceiling can be driven
 * over, so those rows are skipped before any block state is read; open ground therefore costs a handful of lookups
 * instead of one per block under the hull. Ground checks use the chunk heightmap rather than scanning downwards.</p>
 */
public final class VehicleTerrain {

    private static final double STEP = 1.0D;

    private VehicleTerrain() {
    }

    public static boolean boxBlocked(Level level, AABB box, double stepHeight) {
        double climbCeiling = box.minY + Math.max(0.0D, stepHeight);
        int minX = Mth.floor(box.minX + 0.01D);
        int maxX = Mth.floor(box.maxX - 0.01D);
        int minZ = Mth.floor(box.minZ + 0.01D);
        int maxZ = Mth.floor(box.maxZ - 0.01D);
        int minY = Mth.floor(box.minY + 0.01D);
        int maxY = Mth.floor(box.maxY - 0.01D);
        // Every block in row y has its top at most y + 1, so rows below floor(climbCeiling) can never block.
        int startY = Math.max(minY, Mth.floor(climbCeiling));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = startY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (!shape.isEmpty() && shape.max(Direction.Axis.Y) + y > climbCeiling) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static double clearance(Level level, AABB box, Vec3 direction, double max, double stepHeight) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return max;
        }
        Vec3 unit = flat.normalize();
        for (double travelled = STEP; travelled <= max; travelled += STEP) {
            AABB moved = box.move(unit.x * travelled, 0.0D, unit.z * travelled);
            if (boxBlocked(level, moved, stepHeight)) {
                return Math.max(0.0D, travelled - STEP);
            }
        }
        return max;
    }

    public static boolean groundUnder(Level level, AABB box, double maxDrop) {
        double centreX = (box.minX + box.maxX) * 0.5D;
        double centreZ = (box.minZ + box.maxZ) * 0.5D;
        if (hasGround(level, centreX, box.minY, centreZ, maxDrop)) {
            return true;
        }
        return hasGround(level, box.minX + 0.3D, box.minY, box.minZ + 0.3D, maxDrop)
                || hasGround(level, box.maxX - 0.3D, box.minY, box.minZ + 0.3D, maxDrop)
                || hasGround(level, box.minX + 0.3D, box.minY, box.maxZ - 0.3D, maxDrop)
                || hasGround(level, box.maxX - 0.3D, box.minY, box.maxZ - 0.3D, maxDrop);
    }

    /** One heightmap lookup instead of a downward block scan. */
    private static boolean hasGround(Level level, double x, double y, double z, double maxDrop) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(x, y, z));
        double groundTop = surface.getY();
        return groundTop <= y + 1.0D && groundTop >= y - maxDrop - 1.0D;
    }

    public static double groundDrop(Level level, double x, double y, double z, double maxDrop) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(x, y, z));
        double drop = y - surface.getY();
        return drop > maxDrop ? -1.0D : Math.max(0.0D, drop);
    }
}
