package com.curiats.returncapsule.entity.fall;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.block.CapsuleStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class CapsuleBreaker {

    public static int smash(Level level, double centerX, double centerZ, double capsuleBottomY,
                            double capsuleHeight, int floorY, @Nullable net.minecraft.world.entity.Entity source) {
        if (isAirborne(level, centerX, centerZ, capsuleBottomY, capsuleHeight)) {
            return 0;
        }
        int inner = Config.SMASH_INNER_RADIUS.get();
        int outer = Math.max(inner, Config.SMASH_OUTER_RADIUS.get());
        double outerChance = Config.SMASH_OUTER_CHANCE.get();
        int broken = 0;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double x;
                double z;
                if (Math.abs(dx) <= inner && Math.abs(dz) <= inner) {
                    x = centerX + dx * 0.9D;
                    z = centerZ + dz * 0.9D;
                } else {
                    if (outerChance <= 0.0D || level.random.nextFloat() >= outerChance) {
                        continue;
                    }
                    x = centerX + dx * 0.95D + level.random.nextDouble() * 0.3D - 0.15D;
                    z = centerZ + dz * 0.95D + level.random.nextDouble() * 0.3D - 0.15D;
                }
                broken += smashColumn(level, x, z, capsuleBottomY, capsuleHeight, floorY, source);
            }
        }
        return broken;
    }

    private static boolean isAirborne(Level level, double centerX, double centerZ, double bottomY, double height) {
        int minY = (int) Math.floor(bottomY) - 1;
        int maxY = (int) Math.floor(bottomY + height);
        int radius = Config.SMASH_INNER_RADIUS.get();
        int x = (int) Math.floor(centerX);
        int z = (int) Math.floor(centerZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x + dx, y, z + dz);
                    if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static int smashColumn(Level level, double x, double z, double capsuleBottomY,
                                   double capsuleHeight, int floorY, @Nullable net.minecraft.world.entity.Entity source) {
        int passes = Config.SMASH_COLUMN_PASSES.get();
        int broken = 0;
        double y = capsuleBottomY + capsuleHeight + 1.5D;
        for (int pass = 0; pass < passes; pass++) {
            Vec3 start = new Vec3(x, y, z);
            Vec3 end = new Vec3(x, capsuleBottomY - 1.5D, z);
            BlockHitResult hit = level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
            if (hit.getType() == HitResult.Type.MISS) {
                break;
            }
            BlockPos pos = hit.getBlockPos();
            if (pos.getY() < floorY) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                break;
            }
            if (CapsuleStructure.isCapsuleStructure(level, pos)) {
                if (CapsuleStructure.crush(level, pos)) {
                    broken++;
                }
                break;
            }
            if (removeSilently(level, pos)) {
                broken++;
            }
            y = hit.getLocation().y - 0.05D;
        }
        return broken;
    }

    private static boolean removeSilently(Level level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) {
            return false;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        return true;
    }

    private CapsuleBreaker() {
    }
}
