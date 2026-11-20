package com.curiats.returncapsule.compat.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Terrain queries by reading blocks, not by casting rays.
 *
 * <p>A ray only proves that one line is clear; a hull is a box, so a single ray down the middle happily reports room
 * where a corner is already inside a post. Walking the hull's box over the block grid answers the actual question -
 * can this box be here - and costs a handful of block lookups.</p>
 */
public final class VehicleTerrain {

    private static final double STEP = 0.5D;

    private VehicleTerrain() {
    }

    /**
     * True when a block inside {@code box} stands too tall to climb.
     *
     * <p>A collision shape whose top is at or below hull foot plus {@code stepHeight} is a step, not a wall: the hull
     * rolls up onto it. That is what lets a tracked hull take a two block kerb while a wall still stops it.</p>
     */
    public static boolean boxBlocked(Level level, AABB box, double stepHeight) {
        double climbCeiling = box.minY + Math.max(0.0D, stepHeight);
        BlockPos min = BlockPos.containing(box.minX + 0.01D, box.minY + 0.01D, box.minZ + 0.01D);
        BlockPos max = BlockPos.containing(box.maxX - 0.01D, box.maxY - 0.01D, box.maxZ - 0.01D);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double top = shape.max(net.minecraft.core.Direction.Axis.Y) + pos.getY();
            if (top > climbCeiling) {
                return true;
            }
        }
        return false;
    }

    /** How far the box can slide along {@code direction} before terrain blocks it, capped at {@code max}. */
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

    /**
     * True when the footprint has something to roll on.
     *
     * <p>Deliberately lenient: any block that is not fully passable counts as ground, and one supporting point under
     * the hull is enough. Requiring all four corners used to declare slightly uneven flat ground impassable, which
     * sent the driver into the obstacle detour over nothing.</p>
     */
    public static boolean groundUnder(Level level, AABB box, double maxDrop) {
        double centreX = (box.minX + box.maxX) * 0.5D;
        double centreZ = (box.minZ + box.maxZ) * 0.5D;
        if (groundDrop(level, centreX, box.minY, centreZ, maxDrop) >= 0.0D) {
            return true;
        }
        double[][] samples = {
                {box.minX + 0.3D, box.minZ + 0.3D},
                {box.maxX - 0.3D, box.minZ + 0.3D},
                {box.minX + 0.3D, box.maxZ - 0.3D},
                {box.maxX - 0.3D, box.maxZ - 0.3D}
        };
        for (double[] sample : samples) {
            if (groundDrop(level, sample[0], box.minY, sample[1], maxDrop) >= 0.0D) {
                return true;
            }
        }
        return false;
    }

    /** Distance down to the first block with a collision shape, or -1 when there is none within {@code maxDrop}. */
    public static double groundDrop(Level level, double x, double y, double z, double maxDrop) {
        BlockPos origin = BlockPos.containing(x, y, z);
        int limit = (int) Math.ceil(maxDrop) + 1;
        for (int dy = 0; dy <= limit; dy++) {
            BlockPos pos = origin.below(dy);
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return y - (pos.getY() + 1.0D);
            }
        }
        return -1.0D;
    }
}
