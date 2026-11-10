package com.curiats.returncapsule.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class BeaconRange {
    private static final int MAX_RANGE = 50;

    public static boolean isActiveNear(ServerLevel level, BlockPos pos) {
        int radius = (MAX_RANGE >> 4) + 1;
        int centerX = pos.getX() >> 4;
        int centerZ = pos.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof BeaconBlockEntity beacon)) {
                        continue;
                    }
                    int range = range(beacon);
                    if (range > 0 && beacon.getBlockPos().distSqr(pos) <= (double) range * range) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int range(BeaconBlockEntity beacon) {
        int levels = beacon.getBeamSections().size();
        return levels <= 0 ? 0 : 10 + 10 * levels;
    }

    private BeaconRange() {
    }
}
