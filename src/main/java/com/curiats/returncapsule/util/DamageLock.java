package com.curiats.returncapsule.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DamageLock {

    public static final float SHARE = 0.03F;

    private static final float DECAY_PER_SECOND = 0.0F;

    private static final float MAX_LOCK_FRACTION = 1.0F;

    private record Lock(long second, float amount) {
    }

    private static final Map<Integer, Lock> LOCKS = new ConcurrentHashMap<>();

    private DamageLock() {
    }

    public static float lock(LivingEntity target, long gameTime) {
        long second = gameTime / 20L;
        float share = target.getMaxHealth() * SHARE;
        Lock existing = LOCKS.get(target.getId());
        float amount = share;
        if (existing != null) {
            float decayed = existing.amount() - (second - existing.second()) * target.getMaxHealth() * DECAY_PER_SECOND;
            amount = Math.max(0.0F, decayed) + share;
        }
        float cap = target.getMaxHealth() * MAX_LOCK_FRACTION;
        amount = Math.min(amount, cap);
        LOCKS.put(target.getId(), new Lock(second, amount));
        return share;
    }

    public static float locked(LivingEntity target, long gameTime) {
        Lock existing = LOCKS.get(target.getId());
        if (existing == null) {
            return 0.0F;
        }
        long second = gameTime / 20L;
        float decayed = existing.amount() - (second - existing.second()) * target.getMaxHealth() * DECAY_PER_SECOND;
        return Math.max(0.0F, decayed);
    }

    public static void clearAll() {
        LOCKS.clear();
    }

    public static void forget(LivingEntity target) {
        LOCKS.remove(target.getId());
    }
}
