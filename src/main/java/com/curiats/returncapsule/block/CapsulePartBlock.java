package com.curiats.returncapsule.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

@SuppressWarnings({"this-escape",
        "deprecation"})
public class CapsulePartBlock extends Block {

    /** Index range 0..17 belongs to the 3x3 supply capsule. */
    public static final int SUPPLY_PARTS = 18;

    /** One 5x5x3 footprint has 75 cells, of which one is the main block: 18 + 75 - 1. */
    public static final int MAX_PART = SUPPLY_PARTS + 5 * 5 * 3 - 1;

    /** 0..17 describe the 3x3 supply capsule, 18..92 the 5x5 manned capsule. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, MAX_PART);

    private static final VoxelShape EDGE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final VoxelShape OUTER_EDGE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    private static final VoxelShape OUTER_CORNER = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final VoxelShape CORNER = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    public CapsulePartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, 0));
    }

    /**
     * Decodes the offset from the main block for a part index. Draft change: shared by {@link CapsuleStructure} so the
     * main block can be found exactly instead of scanning the neighbourhood.
     *
     * @return {@code {dx, dy, dz}} relative to the main block
     */
    public static int[] offsetOf(int part) {
        if (part >= SUPPLY_PARTS) {
            int local = part - SUPPLY_PARTS;
            return new int[]{local % 5 - 2, local / 5 / 5, (local / 5) % 5 - 2};
        }
        return new int[]{part % 3 - 1, part / 9, (part / 3) % 3 - 1};
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    private static VoxelShape shapeFor(BlockState state) {
        int part = state.getValue(PART);
        if (part >= SUPPLY_PARTS) {
            int local = part - SUPPLY_PARTS;
            int dx = local % 5 - 2;
            int dz = (local / 5) % 5 - 2;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                return Shapes.block();
            }
            // Octagonal approximation of the round hull: edge cells keep the inner 3/4, corners keep the inner
            // corner, so the collision matches the visible silhouette instead of a full cube.
            double minX = dx == -2 ? 0.25D : 0.0D;
            double maxX = dx == 2 ? 0.75D : 1.0D;
            double minZ = dz == -2 ? 0.25D : 0.0D;
            double maxZ = dz == 2 ? 0.75D : 1.0D;
            if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                if (dx == 2) {
                    maxX = 0.5625D;
                } else {
                    minX = 0.4375D;
                }
                if (dz == 2) {
                    maxZ = 0.5625D;
                } else {
                    minZ = 0.4375D;
                }
            }
            return Shapes.box(minX, 0.0D, minZ, maxX, 1.0D, maxZ);
        }
        int dx = part % 3 - 1;
        int dz = (part / 3) % 3 - 1;
        if (dx == 0 && dz == 0) {
            return Shapes.block();
        }
        if (dx == 0 || dz == 0) {
            return EDGE;
        }
        return CORNER;
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
