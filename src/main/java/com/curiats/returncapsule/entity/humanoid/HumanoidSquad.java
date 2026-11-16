package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HumanoidSquad {
    private static final Map<ResourceKey<Level>, Map<UUID, Long>> REPORTS = new ConcurrentHashMap<>();

    private static final int TTL = 200;

    public static void report(Level level, LivingEntity target) {
        if (level.isClientSide) {
            return;
        }
        REPORTS.computeIfAbsent(level.dimension(), key -> new ConcurrentHashMap<>())
                .put(target.getUUID(), level.getGameTime());
    }

    public static List<UUID> active(Level level) {
        Map<UUID, Long> reports = REPORTS.get(level.dimension());
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        long now = level.getGameTime();
        reports.entrySet().removeIf(entry -> now - entry.getValue() > TTL);
        return new ArrayList<>(reports.keySet());
    }

    public static void clear() {
        REPORTS.clear();
    }

    private HumanoidSquad() {
    }
}
