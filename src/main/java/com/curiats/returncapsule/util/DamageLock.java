package com.curiats.returncapsule.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Health that a victim cannot get back.
 * <p>
 * Percentage damage that can simply be healed away is pointless against something that regenerates quickly, and SRP
 * parasites do exactly that. Every percentage hit therefore records a lock: the victim's health is capped at
 * {@code maxHealth - locked} for a while, so the damage is permanent until the lock wears off. The lock decays on its own
 * so a single fight does not weaken a mob forever.
 */
public final class DamageLock {
    /** Fraction of the victim's maximum health that each lock hit denies. */
    public static final float SHARE = 0.03F;
    /** Share of the lock removed per second, so it fades if the fight ends. */
    private static final float DECAY_PER_SECOND = 0.0F;
    /** Cap, so a long fight cannot make a mob unhealable for good. */
    private static final float MAX_LOCK_FRACTION = 1.0F;

    private record Lock(long second, float amount) {
    }

    private static final Map<Integer, Lock> LOCKS = new ConcurrentHashMap<>();

    private DamageLock() {
    }

    /** Records a lock for a victim and returns how much health it just locked away. */
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

    /** How much health the victim is currently denied, counting decay since it was last locked. */
    public static float locked(LivingEntity target, long gameTime) {
        Lock existing = LOCKS.get(target.getId());
        if (existing == null) {
            return 0.0F;
        }
        long second = gameTime / 20L;
        float decayed = existing.amount() - (second - existing.second()) * target.getMaxHealth() * DECAY_PER_SECOND;
        return Math.max(0.0F, decayed);
    }

    /**
     * Drops every lock. Called when the humanoid that applied them dies: the seal holds only while a guard is alive to
     * keep it, so a dead humanoid does not permanently cripple everything it had touched.
     */
    public static void clearAll() {
        LOCKS.clear();
    }

    /** Forgets a victim, called when it dies so the map cannot grow forever. */
    public static void forget(LivingEntity target) {
        LOCKS.remove(target.getId());
    }
}
