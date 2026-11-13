package com.curiats.returncapsule.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public final class BombChunkLoader {
    private static final Map<ResourceKey<Level>, Map<ChunkPos, Integer>> LOADED = new HashMap<>();

    public static void force(ServerLevel level, ChunkPos chunk) {
        Map<ChunkPos, Integer> chunks = LOADED.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        int count = chunks.getOrDefault(chunk, 0);
        chunks.put(chunk, count + 1);
        if (count == 0) {
            level.setChunkForced(chunk.x, chunk.z, true);
        }
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
