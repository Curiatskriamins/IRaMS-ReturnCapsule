package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class HumanoidTraversal {

    private static final int MAX_SAFE_DROP = 3;
    private static final int DEScent_SCAN_RADIUS = 16;

    private HumanoidTraversal() {
    }

    public static boolean isHazard(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (state.is(Blocks.COBWEB) || state.is(Blocks.TRIPWIRE) || state.is(Blocks.TRIPWIRE_HOOK)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.LAVA) || state.is(Blocks.WATER) || state.is(Blocks.SCAFFOLDING)) {
            return true;
        }
        if (state.is(BlockTags.TRAPDOORS) && !state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)) {
            return true;
        }
        return state.is(BlockTags.CLIMBABLE) && !state.is(Blocks.LADDER);
    }

    public static boolean canStandOn(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        if (floor.getCollisionShape(level, below).isEmpty()) {
            return false;
        }
        if (isHazard(level, below) || isHazard(level, pos) || isHazard(level, pos.above())) {
            return false;
        }
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    public static boolean gapAhead(Level level, BlockPos feet, double dirX, double dirZ, int maxGap) {
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-3D) {
            return false;
        }
        dirX /= length;
        dirZ /= length;
        boolean gap = false;
        for (double distance = 0.6D; distance <= 1.6D; distance += 0.5D) {
            int x = (int) Math.floor(feet.getX() + 0.5D + dirX * distance);
            int z = (int) Math.floor(feet.getZ() + 0.5D + dirZ * distance);
            BlockPos probe = new BlockPos(x, feet.getY() - 1, z);
            if (level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
                gap = true;
                break;
            }
        }
        if (!gap) {
            return false;
        }
        for (double distance = 1.6D; distance <= maxGap; distance += 0.5D) {
            int x = (int) Math.floor(feet.getX() + 0.5D + dirX * distance);
            int z = (int) Math.floor(feet.getZ() + 0.5D + dirZ * distance);
            BlockPos landing = new BlockPos(x, feet.getY() - 1, z);
            if (!level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()) {
                return !isHazard(level, landing.above()) && !isHazard(level, landing.above(2));
            }
        }
        return false;
    }

    public static boolean nearHighEdge(Level level, BlockPos feet) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : directions) {
            for (int step = 2; step <= 6; step++) {
                BlockPos probe = feet.offset(dir[0] * step, 0, dir[1] * step);
                BlockPos below = probe.below();
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    continue;
                }
                if (platformDrop(level, probe) >= 8) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isClimbable(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(net.minecraft.tags.BlockTags.CLIMBABLE);
    }

    public static int ladderDrop(Level level, BlockPos ladderBottom) {
        for (int drop = 1; drop <= 32; drop++) {
            BlockPos probe = ladderBottom.below(drop);
            if (!level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
                return drop - 1;
            }
        }
        return 32;
    }

    public static boolean isTrapped(Level level, BlockPos feet) {
        int walls = 0;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            boolean blocked = false;
            for (int height = 0; height <= 1; height++) {
                BlockPos probe = feet.relative(direction).above(height);
                if (!level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) {
                walls++;
            }
        }
        return walls >= 4;
    }

    public static int platformDrop(Level level, BlockPos feet) {
        for (int drop = 1; drop <= 32; drop++) {
            BlockPos probe = feet.below(drop);
            if (!level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
                return drop;
            }
        }
        return 32;
    }

    public static BlockPos landingOf(Level level, BlockPos feet, double dirX, double dirZ, int maxGap) {
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-3D) {
            return null;
        }
        dirX /= length;
        dirZ /= length;
        boolean gap = false;
        for (double distance = 0.4D; distance <= 0.8D; distance += 0.3D) {
            BlockPos probe = new BlockPos((int) Math.floor(feet.getX() + 0.5D + dirX * distance),
                    feet.getY() - 1,
                    (int) Math.floor(feet.getZ() + 0.5D + dirZ * distance));
            if (level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
                gap = true;
                break;
            }
        }
        if (!gap) {
            return null;
        }
        for (double distance = 1.0D; distance <= maxGap; distance += 0.4D) {
            BlockPos landing = new BlockPos((int) Math.floor(feet.getX() + 0.5D + dirX * distance),
                    feet.getY() - 1,
                    (int) Math.floor(feet.getZ() + 0.5D + dirZ * distance));
            if (!level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()) {
                BlockPos stand = landing.above();
                if (isHazard(level, stand) || isHazard(level, stand.above())) {
                    return null;
                }
                return stand;
            }
        }
        return null;
    }

    public static double corridorOffset(Level level, BlockPos feet, double dirX, double dirZ, int maxLateral) {
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-3D) {
            return 0.0D;
        }
        double px = -dirZ / length;
        double pz = dirX / length;
        int positive = 0;
        int negative = 0;
        for (int step = 1; step <= maxLateral; step++) {
            if (!walkableAt(level, feet, px * step, pz * step)) {
                break;
            }
            positive = step;
        }
        for (int step = 1; step <= maxLateral; step++) {
            if (!walkableAt(level, feet, -px * step, -pz * step)) {
                break;
            }
            negative = step;
        }
        return (positive - negative) * 0.5D;
    }

    private static boolean walkableAt(Level level, BlockPos feet, double offsetX, double offsetZ) {
        BlockPos probe = BlockPos.containing(feet.getX() + 0.5D + offsetX, feet.getY(),
                feet.getZ() + 0.5D + offsetZ);
        return canStandOn(level, probe);
    }

    public static boolean headBlocked(Level level, BlockPos pos) {
        BlockPos head = pos.above();
        return !level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    public static boolean stepUpAhead(Level level, BlockPos pos, double dirX, double dirZ) {
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-3D) {
            return false;
        }
        int x = (int) Math.floor(pos.getX() + 0.5D + dirX / length);
        int z = (int) Math.floor(pos.getZ() + 0.5D + dirZ / length);
        BlockPos feet = new BlockPos(x, pos.getY(), z);
        BlockPos head = feet.above();
        boolean feetSolid = !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
        boolean headFree = level.getBlockState(head).getCollisionShape(level, head).isEmpty();
        return feetSolid && headFree;
    }

    public static double gapWidthAhead(Level level, BlockPos feet, double dirX, double dirZ, int maxGap) {
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-3D) {
            return -1.0D;
        }
        dirX /= length;
        dirZ /= length;
        double edge = -1.0D;
        for (double distance = 0.4D; distance <= 2.0D; distance += 0.25D) {
            int x = (int) Math.floor(feet.getX() + 0.5D + dirX * distance);
            int z = (int) Math.floor(feet.getZ() + 0.5D + dirZ * distance);
            BlockPos below = new BlockPos(x, feet.getY() - 1, z);
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                edge = distance;
                break;
            }
        }
        if (edge < 0.0D) {
            return -1.0D;
        }
        for (double distance = edge + 0.4D; distance <= maxGap; distance += 0.4D) {
            int x = (int) Math.floor(feet.getX() + 0.5D + dirX * distance);
            int z = (int) Math.floor(feet.getZ() + 0.5D + dirZ * distance);
            BlockPos landing = new BlockPos(x, feet.getY() - 1, z);
            if (!level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()) {
                boolean safe = !isHazard(level, landing.above())
                        && !isHazard(level, landing.above(2));
                return safe ? distance : -1.0D;
            }
        }
        return -1.0D;
    }

    public static BlockPos findLandingSpot(Level level, BlockPos around, int radius) {
        for (int dy = 2; dy >= -3; dy--) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    BlockPos candidate = around.offset(dx, dy, dz);
                    if (canStandOn(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos findDescentSpot(Level level, BlockPos from, BlockPos toward) {
        int bestScore = Integer.MAX_VALUE;
        BlockPos best = null;
        for (int dx = -DEScent_SCAN_RADIUS; dx <= DEScent_SCAN_RADIUS; dx += 2) {
            for (int dz = -DEScent_SCAN_RADIUS; dz <= DEScent_SCAN_RADIUS; dz += 2) {
                BlockPos edge = from.offset(dx, 0, dz);
                if (!canStandOn(level, edge)) {
                    continue;
                }
                if (edge.getY() > toward.getY() + 1) {
                    continue;
                }
                BlockPos landing = findLanding(level, edge);
                if (landing == null) {
                    continue;
                }
                int drop = edge.getY() - landing.getY();
                if (drop <= 0 || drop > MAX_SAFE_DROP) {
                    continue;
                }
                int score = (int) (edge.distSqr(from) * 2.0D) + (int) landing.distSqr(toward);
                if (score < bestScore) {
                    bestScore = score;
                    best = edge;
                }
            }
        }
        return best;
    }

    private static BlockPos findLanding(Level level, BlockPos edge) {
        for (int drop = 1; drop <= MAX_SAFE_DROP + 2; drop++) {
            BlockPos candidate = edge.below(drop);
            if (canStandOn(level, candidate)) {
                return candidate;
            }
            if (!level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && !isHazard(level, candidate)) {
                return null;
            }
        }
        return null;
    }
}
