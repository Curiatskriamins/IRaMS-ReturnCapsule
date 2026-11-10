package com.curiats.returncapsule.block;

import com.curiats.returncapsule.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class CapsulePartBlock extends Block {
    public CapsulePartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                  net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos main = CapsuleStructure.findMain(level, pos);
            if (main != null) {
                return CapsuleInteraction.use(level, main, player, hand, hit);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        level.addDestroyBlockEffect(pos, Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            destroyCore(level, pos, player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            destroyCore(level, pos, null);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    private static void destroyCore(Level level, BlockPos part, @Nullable Player player) {
        BlockPos main = findMain(level, part);
        if (main == null) {
            return;
        }
        BlockState mainState = level.getBlockState(main);
        if (!(mainState.getBlock() instanceof CapsuleBlock capsule)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(main);
        if (player != null) {
            capsule.playerWillDestroy(level, main, mainState, player);
        } else if (blockEntity instanceof CapsuleBlockEntity container && level instanceof ServerLevel serverLevel) {
            container.ensureLootTable();
            net.minecraft.world.Containers.dropContents(serverLevel, main, container);
            container.clearContent();
        }
        if (player != null && !player.isCreative() && level instanceof ServerLevel serverLevel) {
            Block.dropResources(mainState, serverLevel, main, blockEntity, player, player.getMainHandItem());
        }
        level.removeBlock(main, false);
    }

    @Nullable
    private static BlockPos findMain(Level level, BlockPos part) {
        return CapsuleStructure.findMain(level, part);
    }
}
