package com.curiats.returncapsule.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DamageLock {

    private static final float DEFAULT_SHARE = 0.03F;

    private static final float DECAY_PER_SECOND = 0.0F;

    private static final float MAX_LOCK_FRACTION = 1.0F;

    private record Lock(long second, float amount, long expiry) {
    }

    private static final Map<Integer, Lock> LOCKS = new ConcurrentHashMap<>();

    private DamageLock() {
    }

    public static void lockFraction(LivingEntity target, long gameTime, float fraction, int durationTicks) {
        long second = gameTime / 20L;
        float share = target.getMaxHealth() * fraction;
        float cap = target.getMaxHealth() * MAX_LOCK_FRACTION;
        LOCKS.put(target.getId(), new Lock(second, Math.min(share, cap), gameTime + durationTicks));
    }

    public static void lockFraction(LivingEntity target, long gameTime, float fraction) {
        long second = gameTime / 20L;
        float share = target.getMaxHealth() * fraction;
        Lock existing = LOCKS.get(target.getId());
        float amount = share;
        if (existing != null) {
            amount = Math.max(0.0F, existing.amount()) + share;
        }
        float cap = target.getMaxHealth() * MAX_LOCK_FRACTION;
        LOCKS.put(target.getId(), new Lock(second, Math.min(amount, cap), Long.MAX_VALUE));
    }

    public static float lock(LivingEntity target, long gameTime) {
        long second = gameTime / 20L;
        float share = target.getMaxHealth() * share();
        Lock existing = LOCKS.get(target.getId());
        float amount = share;
        if (existing != null) {
            float decayed = existing.amount() - (second - existing.second()) * target.getMaxHealth() * DECAY_PER_SECOND;
            amount = Math.max(0.0F, decayed) + share;
        }
        float cap = target.getMaxHealth() * MAX_LOCK_FRACTION;
        amount = Math.min(amount, cap);
        LOCKS.put(target.getId(), new Lock(second, amount, Long.MAX_VALUE));
        return share;
    }

    public static float share() {
        try {
            return com.curiats.returncapsule.Config.HUMANOID_SEAL_SHARE.get().floatValue();
        } catch (RuntimeException notLoaded) {
            return DEFAULT_SHARE;
        }
    }

    public static float locked(LivingEntity target, long gameTime) {
        Lock existing = LOCKS.get(target.getId());
        if (existing == null) {
            return 0.0F;
        }
        if (gameTime > existing.expiry()) {
            LOCKS.remove(target.getId());
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
