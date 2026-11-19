package com.curiats.returncapsule.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public final class BombChunkLoader {
    private static final Map<ResourceKey<Level>, Map<ChunkPos, Integer>> LOADED = new HashMap<>();

    /**
     * Hard cap on force loaded chunks across every dimension. A salvo of fifty bombs used to take up to a hundred and
     * fifty chunks of entity ticking area; past the cap a bomb simply stops ticking in an unloaded chunk, which costs
     * one bomb instead of the whole server tick.
     */
    private static final int MAX_FORCED_CHUNKS = 32;

    public static boolean force(ServerLevel level, ChunkPos chunk) {
        Map<ChunkPos, Integer> chunks = LOADED.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        int count = chunks.getOrDefault(chunk, 0);
        if (count > 0) {
            chunks.put(chunk, count + 1);
            return true;
        }
        if (totalForced() >= MAX_FORCED_CHUNKS) {
            return false;
        }
        chunks.put(chunk, 1);
        level.setChunkForced(chunk.x, chunk.z, true);
        return true;
    }

    private static int totalForced() {
        int total = 0;
        for (Map<ChunkPos, Integer> chunks : LOADED.values()) {
            total += chunks.size();
        }
        return total;
    }

    public static void release(ServerLevel level, ChunkPos chunk) {
        Map<ChunkPos, Integer> chunks = LOADED.get(level.dimension());
        if (chunks == null) {
            return;
        }
        Integer count = chunks.get(chunk);
        if (count == null) {
            return;
        }
        if (count <= 1) {
            chunks.remove(chunk);
            level.setChunkForced(chunk.x, chunk.z, false);
        } else {
            chunks.put(chunk, count - 1);
        }
    }

    private BombChunkLoader() {
    }
}
