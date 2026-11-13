package com.curiats.returncapsule.entity.fall;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.block.CapsuleStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public final class LandingSite {
    private static final int SURFACE_SEARCH_DEPTH = 8;
    private static final int DROP_RADIUS = 2;
    private static final int DROP_SEARCH_HEIGHT = 4;
    private static final int MAX_DROPS = 10;

    public static BlockPos findLanding(ServerLevel level, BlockPos anchor) {
        int maxSurface = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int floor = anchor.getY() - SURFACE_SEARCH_DEPTH;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = anchor.getY(); y >= floor; y--) {
                    cursor.set(anchor.getX() + dx, y, anchor.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.isCollisionShapeFullBlock(level, cursor)) {
                        maxSurface = Math.max(maxSurface, y + 1);
                        break;
                    }
                    if (state.getDestroySpeed(level, cursor) < 0.0F) {
                        break;
                    }
                    if (CapsuleStructure.isCapsuleStructure(level, cursor)) {
                        CapsuleStructure.crush(level, cursor);
                        break;
                    }
                    if (Config.CAPSULE_BREAK_BLOCKS.get()) {
                        level.destroyBlock(cursor.immutable(), false);
                    }
                }
            }
        }
        if (maxSurface == Integer.MIN_VALUE) {
            return anchor;
        }
        return new BlockPos(anchor.getX(), maxSurface, anchor.getZ());
    }

    public static void clearArea(ServerLevel level, BlockPos anchor) {
        if (!Config.CAPSULE_BREAK_BLOCKS.get()) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < 2; dy++) {
                    cursor.setWithOffset(anchor, dx, dy, dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.getDestroySpeed(level, cursor) < 0.0F) {
                        continue;
                    }
                    if (CapsuleStructure.isCapsuleStructure(level, cursor)) {
                        CapsuleStructure.crush(level, cursor);
                        continue;
                    }
                    level.destroyBlock(cursor.immutable(), false);
                }
            }
        }
    }

    public static int scatterDrops(ServerLevel level, BlockPos anchor, RandomSource random) {
        if (!Config.CAPSULE_BREAK_BLOCKS.get()) {
            return 0;
        }
        int drops = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -DROP_RADIUS; dx <= DROP_RADIUS && drops < MAX_DROPS; dx++) {
            for (int dz = -DROP_RADIUS; dz <= DROP_RADIUS && drops < MAX_DROPS; dz++) {
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    continue;
                }
                if (random.nextFloat() > Config.LANDING_DROP_CHANCE.get()) {
                    continue;
                }
                BlockPos surface = null;
                for (int dy = DROP_SEARCH_HEIGHT; dy >= 0; dy--) {
                    cursor.setWithOffset(anchor, dx, dy, dz);
                    if (!level.getBlockState(cursor).isAir()) {
                        surface = cursor.immutable();
                        break;
                    }
                }
                if (surface == null) {
                    continue;
                }
                BlockState state = level.getBlockState(surface);
                if (state.getDestroySpeed(level, surface) < 0.0F) {
                    continue;
                }
                if (state.getBlock().getExplosionResistance() > 100.0F) {
                    continue;
                }
                if (CapsuleStructure.isCapsuleStructure(level, surface)) {
                    CapsuleStructure.crush(level, surface);
                    continue;
                }
                if (level.destroyBlock(surface, true)) {
                    drops++;
                }
            }
        }
        return drops;
    }

    private LandingSite() {
    }
}
