package com.curiats.returncapsule.entity.fall;

import com.curiats.returncapsule.block.CapsuleBlock;
import com.curiats.returncapsule.block.CapsuleBlockEntity;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.registry.ModBlocks;
import com.curiats.returncapsule.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class CapsulePlacer {
    public static void withLootTable(ServerLevel level, BlockPos pos, Direction facing,
                                     ResourceLocation lootTable, SummonTier tier) {
        if (place(level, pos, facing, tier)) {
            if (level.getBlockEntity(pos) instanceof CapsuleBlockEntity capsule) {
                capsule.setLootTable(lootTable, level.random.nextLong());
            }
        } else {
            dropFallback(level, pos);
        }
    }

    public static void withItems(ServerLevel level, BlockPos pos, Direction facing,
                                 Container source, SummonTier tier) {
        if (place(level, pos, facing, tier)) {
            if (level.getBlockEntity(pos) instanceof CapsuleBlockEntity capsule) {
                int slots = Math.min(capsule.getContainerSize(), source.getContainerSize());
                for (int i = 0; i < slots; i++) {
                    capsule.setItem(i, source.getItem(i));
                }
                capsule.markLootGenerated();
            }
        } else {
            dropFallback(level, pos);
        }
    }

    private static boolean place(ServerLevel level, BlockPos pos, Direction facing, SummonTier tier) {
        BlockState state = ModBlocks.CAPSULE.get().defaultBlockState().setValue(CapsuleBlock.FACING, facing);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof CapsuleBlockEntity capsule) {
            capsule.setTier(tier);
        }
        return true;
    }

    private static void dropFallback(ServerLevel level, BlockPos pos) {
        Block.popResource(level, pos, new ItemStack(ModItems.CAPSULE.get()));
    }

    private CapsulePlacer() {
    }
}
