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
            dropFallback(level, pos, CapsuleKind.SUPPLY);
        }
    }

    public static void withItems(ServerLevel level, BlockPos pos, Direction facing,
                                 Container source, SummonTier tier) {
        withItems(level, pos, facing, source, tier, CapsuleKind.SUPPLY);
    }

    public static void withItems(ServerLevel level, BlockPos pos, Direction facing,
                                 Container source, SummonTier tier, CapsuleKind kind) {
        if (place(level, pos, facing, tier, kind)) {
            if (level.getBlockEntity(pos) instanceof CapsuleBlockEntity capsule) {
                int slots = Math.min(capsule.getContainerSize(), source.getContainerSize());
                for (int i = 0; i < slots; i++) {
                    capsule.setItem(i, source.getItem(i));
                }
                capsule.markLootGenerated();
            }
        } else {
            dropFallback(level, pos, kind);
        }
    }

    private static boolean place(ServerLevel level, BlockPos pos, Direction facing, SummonTier tier) {
        return place(level, pos, facing, tier, CapsuleKind.SUPPLY);
    }

    private static boolean place(ServerLevel level, BlockPos pos, Direction facing, SummonTier tier,
                                 CapsuleKind kind) {
        var block = kind == CapsuleKind.MANNED ? ModBlocks.MANNED_CAPSULE.get() : ModBlocks.CAPSULE.get();
        BlockState state = block.defaultBlockState().setValue(CapsuleBlock.FACING, facing);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof CapsuleBlockEntity capsule) {
            capsule.setTier(tier);
        }
        return true;
    }

    /** a failed manned placement must not fall back to a supply capsule item. */
    private static void dropFallback(ServerLevel level, BlockPos pos, CapsuleKind kind) {
        ItemStack drop = kind == CapsuleKind.MANNED
                ? new ItemStack(ModItems.MANNED_CAPSULE.get())
                : new ItemStack(ModItems.CAPSULE.get());
        Block.popResource(level, pos, drop);
    }

    private CapsulePlacer() {
    }
}
