package com.curiats.returncapsule.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Healing seal applied by base humanoid hits.
 *
 * <p>A humanoid does not remove health or max health: it seals a share of the target's pool so that healing cannot
 * bring the target back above {@code maxHealth - sealed}. Contributions are tracked per humanoid, so the sealed pool
 * stays blocked while that humanoid lives and is released the moment it dies.</p>
 */
public final class DamageLock {

    private static final float DECAY_PER_SECOND = 0.0F;

    private static final float MAX_LOCK_FRACTION = 1.0F;

    private record TargetLock(Map<UUID, Float> sources, long second, long expiry) {
    }

    private static final Map<Integer, TargetLock> LOCKS = new ConcurrentHashMap<>();

    private DamageLock() {
    }

    /** Adds this source's share to the seal on {@code target}. */
    public static float lock(LivingEntity target, long gameTime, UUID source) {
        long second = gameTime / 20L;
        float share = target.getMaxHealth() * share();
        TargetLock existing = LOCKS.get(target.getId());
        Map<UUID, Float> from = existing == null ? new HashMap<>() : new HashMap<>(existing.sources());
        float previous = from.getOrDefault(source, 0.0F);
        from.put(source, previous + share);
        float cap = target.getMaxHealth() * MAX_LOCK_FRACTION;
        float total = 0.0F;
        for (float amount : from.values()) {
            total += amount;
        }
        if (total > cap) {
            // Trim the newest contribution so the pool never exceeds the cap.
            float over = total - cap;
            from.put(source, Math.max(0.0F, from.get(source) - over));
        }
        LOCKS.put(target.getId(), new TargetLock(from, second, Long.MAX_VALUE));
        return share;
    }

    public static void lockFraction(LivingEntity target, long gameTime, float fraction, int durationTicks,
            UUID source) {
        long second = gameTime / 20L;
        float share = target.getMaxHealth() * fraction;
        TargetLock existing = LOCKS.get(target.getId());
        Map<UUID, Float> from = existing == null ? new HashMap<>() : new HashMap<>(existing.sources());
        UUID key = source == null ? ZERO : source;
        float previous = from.getOrDefault(key, 0.0F);
        from.put(key, previous + share);
        float cap = target.getMaxHealth() * MAX_LOCK_FRACTION;
        float total = 0.0F;
        for (float amount : from.values()) {
            total += amount;
        }
        if (total > cap) {
            float over = total - cap;
            from.put(key, Math.max(0.0F, from.get(key) - over));
        }
        LOCKS.put(target.getId(), new TargetLock(from, second, gameTime + durationTicks));
    }

    /** Total sealed health on {@code target} right now. */
    public static float locked(LivingEntity target, long gameTime) {
        TargetLock lock = LOCKS.get(target.getId());
        if (lock == null) {
            return 0.0F;
        }
        if (lock.expiry() != Long.MAX_VALUE && gameTime > lock.expiry()) {
            LOCKS.remove(target.getId());
            return 0.0F;
        }
        float total = 0.0F;
        for (float amount : lock.sources().values()) {
            total += Math.max(0.0F, amount);
        }
        if (DECAY_PER_SECOND > 0.0F) {
            long seconds = gameTime / 20L - lock.second();
            total = Math.max(0.0F, total - seconds * target.getMaxHealth() * DECAY_PER_SECOND);
        }
        return Math.min(total, target.getMaxHealth() * MAX_LOCK_FRACTION);
    }

    /** Drops every seal this humanoid applied; called when it dies so the sealed pool may recover. */
    public static void release(UUID source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<Integer, TargetLock> entry : LOCKS.entrySet()) {
            TargetLock lock = entry.getValue();
            if (!lock.sources().containsKey(source)) {
                continue;
            }
            Map<UUID, Float> from = new HashMap<>(lock.sources());
            from.remove(source);
            if (from.isEmpty()) {
                LOCKS.remove(entry.getKey());
            } else {
                LOCKS.put(entry.getKey(), new TargetLock(from, lock.second(), lock.expiry()));
            }
        }
    }

    public static void clearAll() {
        LOCKS.clear();
    }

    public static float share() {
        try {
            return com.curiats.returncapsule.Config.HUMANOID_SEAL_SHARE.get().floatValue();
        } catch (RuntimeException exception) {
            return 0.03F;
        }
    }

    private static final UUID ZERO = new UUID(0L, 0L);
}
