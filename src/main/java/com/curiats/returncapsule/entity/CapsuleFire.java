package com.curiats.returncapsule.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class CapsuleFire {
    public static void spread(ServerLevel level, Vec3 center, double radius, double chance) {
        if (chance <= 0.0D || radius <= 0.0D) {
            return;
        }
        int attempts = Math.max(1, (int) (radius * radius * 8.0D));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++) {
            double x = center.x + (level.random.nextDouble() * 2.0D - 1.0D) * radius;
            double y = center.y + (level.random.nextDouble() * 2.0D - 1.0D) * radius * 0.5D;
            double z = center.z + (level.random.nextDouble() * 2.0D - 1.0D) * radius;
            cursor.set(BlockPos.containing(x, y, z));
            if (!level.isEmptyBlock(cursor)) {
                continue;
            }
            if (level.random.nextDouble() >= chance) {
                continue;
            }
            BlockPos below = cursor.below();
            BlockState belowState = level.getBlockState(below);
            if (!belowState.isFaceSturdy(level, below, Direction.UP)) {
                continue;
            }
            level.setBlockAndUpdate(cursor, BaseFireBlock.getState(level, cursor));
        }
    }

    private CapsuleFire() {
    }
}
