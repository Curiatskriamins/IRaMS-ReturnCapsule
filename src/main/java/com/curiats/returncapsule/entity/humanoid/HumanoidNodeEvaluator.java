package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;

public class HumanoidNodeEvaluator extends WalkNodeEvaluator {

    private Mob mob;

    private final java.util.Map<Long, BlockState> stateCache = new java.util.HashMap<>();

    private static final int CACHE_LIMIT = 8192;

    @Override
    public void prepare(@NotNull PathNavigationRegion region, @NotNull Mob entity) {
        super.prepare(region, entity);
        this.mob = entity;
        this.stateCache.clear();
    }

    @Override
    public int getNeighbors(@NotNull Node[] neighbors, @NotNull Node current) {
        int count = super.getNeighbors(neighbors, current);
        if (this.mob == null) {
            return count;
        }
        try {
            return this.extraNeighbors(neighbors, current, count);
        } catch (RuntimeException exception) {
            return count;
        }
    }

    private int extraNeighbors(Node[] neighbors, Node current, int count) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (count >= neighbors.length - 1) {
                    return count;
                }
                BlockPos next = new BlockPos(current.x + dx, current.y, current.z + dz);
                if (!this.solid(next) && !this.solid(next.below())) {
                    Node jump = this.jumpNode(current, dx, dz);
                    if (jump != null) {
                        neighbors[count++] = jump;
                    }
                    continue;
                }
                if (!this.solid(next)) {
                    continue;
                }
                Node stepUp = this.stepUpNode(current, dx, dz);
                if (stepUp != null) {
                    neighbors[count++] = stepUp;
                    if (count >= neighbors.length - 1) {
                        return count;
                    }
                }
                if (this.climbable(next) || this.climbable(next.below())) {
                    Node climb = this.climbNode(current, dx, dz);
                    if (climb != null) {
                        neighbors[count++] = climb;
                        if (count >= neighbors.length - 1) {
                            return count;
                        }
                    }
                    Node climbDown = this.climbDownNode(current, dx, dz);
                    if (climbDown != null) {
                        neighbors[count++] = climbDown;
                        if (count >= neighbors.length - 1) {
                            return count;
                        }
                    }
                }
                if (!this.climbable(next) && this.climbable(next.above())) {
                    Node ladderJump = this.ladderJumpNode(current, dx, dz);
                    if (ladderJump != null) {
                        neighbors[count++] = ladderJump;
                    }
                }
            }
        }
        return count;
    }

    private Node jumpNode(Node current, int dx, int dz) {
        int gap = 0;
        for (int step = 1; step <= 5; step++) {
            BlockPos probe = new BlockPos(current.x + dx * step, current.y, current.z + dz * step);
            if (!this.solid(probe.below())) {
                gap++;
                continue;
            }
            if (gap == 0 || gap > 4) {
                return null;
            }
            if (!this.open(probe) || !this.open(probe.above())) {
                return null;
            }
            if (this.dangerous(probe) || this.dangerous(probe.above()) || this.dangerous(probe.below())) {
                return null;
            }
            Node landing = this.getNode(probe.getX(), probe.getY(), probe.getZ());
            if (landing == null) {
                return null;
            }
            landing.costMalus = 1.5F + gap;
            return landing;
        }
        return null;
    }

    private Node stepUpNode(Node current, int dx, int dz) {
        BlockPos step = new BlockPos(current.x + dx, current.y, current.z + dz);
        BlockPos feet = step.above();
        if (!this.open(feet) || !this.open(feet.above())) {
            return null;
        }
        if (this.dangerous(feet) || this.dangerous(step)) {
            return null;
        }
        Node landing = this.getNode(feet.getX(), feet.getY(), feet.getZ());
        if (landing == null) {
            return null;
        }
        landing.costMalus = 2.0F;
        return landing;
    }

    private Node climbNode(Node current, int dx, int dz) {
        BlockPos base = new BlockPos(current.x + dx, current.y, current.z + dz);
        if (!this.climbable(base) && !this.climbable(base.below())) {
            return null;
        }
        BlockPos feet = base.above();
        if (!this.open(feet) || !this.open(feet.above())) {
            return null;
        }
        Node landing = this.getNode(feet.getX(), feet.getY(), feet.getZ());
        if (landing == null) {
            return null;
        }
        landing.costMalus = 2.5F;
        return landing;
    }

    private Node climbDownNode(Node current, int dx, int dz) {
        BlockPos base = new BlockPos(current.x + dx, current.y, current.z + dz);
        if (!this.climbable(base)) {
            return null;
        }
        BlockPos below = base.below();
        if (!this.climbable(below)) {
            return null;
        }
        Node landing = this.getNode(below.getX(), below.getY(), below.getZ());
        if (landing == null) {
            return null;
        }
        landing.costMalus = 0.8F;
        return landing;
    }

    private Node ladderJumpNode(Node current, int dx, int dz) {
        BlockPos base = new BlockPos(current.x + dx, current.y, current.z + dz);
        if (this.climbable(base) || !this.climbable(base.above())) {
            return null;
        }
        if (!this.open(base) || !this.open(base.above(2))) {
            return null;
        }
        Node landing = this.getNode(base.getX(), base.getY() + 1, base.getZ());
        if (landing == null) {
            return null;
        }
        landing.costMalus = 3.0F;
        return landing;
    }

    private BlockState state(BlockPos pos) {
        long key = BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
        BlockState cached = this.stateCache.get(key);
        if (cached != null) {
            return cached;
        }
        BlockState state = this.level.getBlockState(pos);
        if (this.stateCache.size() < CACHE_LIMIT) {
            this.stateCache.put(key, state);
        }
        return state;
    }

    private boolean solid(BlockPos pos) {
        BlockState state = this.state(pos);
        return !state.getCollisionShape(this.level, pos, CollisionContext.empty()).isEmpty();
    }

    private boolean open(BlockPos pos) {
        BlockState state = this.state(pos);
        return state.getCollisionShape(this.level, pos, CollisionContext.empty()).isEmpty();
    }

    private boolean climbable(BlockPos pos) {
        return this.state(pos).is(net.minecraft.tags.BlockTags.CLIMBABLE);
    }

    private boolean dangerous(BlockPos pos) {
        BlockState state = this.state(pos);
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.COBWEB) || state.is(Blocks.SCAFFOLDING);
    }
}
