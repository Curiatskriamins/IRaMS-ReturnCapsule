package com.curiats.returncapsule.block;

import com.curiats.returncapsule.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class CapsuleStructure {
    public static final int RADIUS = 1;
    public static final int HEIGHT = 2;

    public static int placeParts(ServerLevel level, BlockPos main) {
        BlockState part = ModBlocks.CAPSULE_PART.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = 0; dy < HEIGHT; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.setWithOffset(main, dx, dy, dz);
                    BlockState existing = level.getBlockState(cursor);
                    if (existing.isAir() || existing.canBeReplaced()) {
                        int index = dy * 9 + (dz + 1) * 3 + (dx + 1);
                        level.setBlock(cursor, part.setValue(CapsulePartBlock.PART, index), Block.UPDATE_ALL);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    public static void removeParts(Level level, BlockPos main) {
        BlockState part = ModBlocks.CAPSULE_PART.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = 0; dy < HEIGHT; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.setWithOffset(main, dx, dy, dz);
                    if (level.getBlockState(cursor).is(part.getBlock())) {
                        level.removeBlock(cursor, false);
                    }
                }
            }
        }
    }

    public static boolean isIntact(Level level, BlockPos main) {
        BlockState part = ModBlocks.CAPSULE_PART.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = 0; dy < HEIGHT; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.setWithOffset(main, dx, dy, dz);
                    if (!level.getBlockState(cursor).is(part.getBlock())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Nullable
    public static BlockPos findMain(Level level, BlockPos probe) {
        BlockState probeState = level.getBlockState(probe);
        if (probeState.is(ModBlocks.CAPSULE.get())) {
            return probe;
        }
        if (!probeState.is(ModBlocks.CAPSULE_PART.get())) {
            return null;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -(HEIGHT - 1); dy <= 0; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.setWithOffset(probe, dx, dy, dz);
                    if (level.getBlockState(cursor).is(ModBlocks.CAPSULE.get())) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    public static boolean isCapsuleStructure(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.CAPSULE.get())) {
            return true;
        }
        return state.is(ModBlocks.CAPSULE_PART.get()) && findMain(level, pos) != null;
    }

    private CapsuleStructure() {
    }
}
