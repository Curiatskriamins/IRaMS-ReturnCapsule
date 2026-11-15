package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared target memory for the humanoids: when one of them decides to fight something, every other humanoid on the
 * same dimension learns about it. That is what makes the group react as a squad instead of each member having to
 * stumble onto the same target inside its own scan box.
 * <p>
 * Reports are keyed by entity id and expire after a few seconds, so a target that has been dealt with (or that simply
 * walked out of the loaded area) is not chased forever.
 */
public final class HumanoidSquad {
    private static final Map<ResourceKey<Level>, Map<UUID, Long>> REPORTS = new ConcurrentHashMap<>();
    /** Report lifetime in ticks. */
    private static final int TTL = 200;

    /** Announced whenever a humanoid acquires a target, and whenever a player or humanoid is attacked. */
    public static void report(Level level, LivingEntity target) {
        if (level.isClientSide) {
            return;
        }
        REPORTS.computeIfAbsent(level.dimension(), key -> new ConcurrentHashMap<>())
                .put(target.getUUID(), level.getGameTime());
    }

    /** Targets the squad has reported recently, on this dimension. */
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
