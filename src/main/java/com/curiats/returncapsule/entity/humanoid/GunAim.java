package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Aim state of one gun-armed humanoid.
 * <p>
 * The behaviour the design asks for is "fire, look at the result, and only keep the same aim if it actually landed".
 * So a shot is treated as <em>in flight</em> until either the target is hit or the projectile has had time to arrive;
 * while a shot is unresolved the humanoid holds its fire, and an unresolved shot counts as a miss that biases the next
 * one.
 * <p>
 * Two corrections are applied to the next shot:
 * <ul>
 *   <li><b>lead</b> — the target is aimed where it will be after the projectile travels the distance, otherwise a
 *       walking target is always missed;</li>
 *   <li><b>miss bias</b> — a small, alternating yaw offset plus a growing elevation offset, so consecutive misses
 *       sweep across the target instead of repeating the same wrong solution. It decays back to zero on a hit.</li>
 * </ul>
 */
public final class GunAim {
    /** Rough projectile speed used for lead and flight-time estimates, in blocks per tick. */
    private static final double BULLET_SPEED = 12.0D;
    private static final int MIN_FLIGHT_TICKS = 10;
    private static final int MAX_FLIGHT_TICKS = 60;
    private static final double PITCH_STEP = 0.4D;
    private static final double MAX_PITCH_BIAS = 8.0D;
    private static final double MAX_YAW_BIAS = 4.0D;
    private static final double MIN_YAW_BIAS = 0.3D;
    /** Bias decays by this factor on a confirmed hit, so a good solution is kept rather than reset. */
    private static final double DECAY = 0.5D;

    private int targetId = -1;
    private long shotTick = Long.MIN_VALUE;
    private int misses;
    private double pitchBias;
    private double yawBias;

    /** Called every tick with the current target, so a new target starts from a clean solution. */
    public void track(LivingEntity target) {
        if (target.getId() != this.targetId) {
            this.reset();
            this.targetId = target.getId();
        }
    }

    /** Records that a shot was just fired; the humanoid holds fire until it resolves. */
    public void onShot(LivingEntity target) {
        this.targetId = target.getId();
        this.shotTick = target.level().getGameTime();
    }

    /** A hit landed on {@code victim}; only counts when it is the target the shot was aimed at. */
    public void onHit(LivingEntity victim) {
        if (victim.getId() != this.targetId) {
            return;
        }
        this.shotTick = Long.MIN_VALUE;
        this.misses = 0;
        this.pitchBias *= DECAY;
        this.yawBias *= DECAY;
    }

    /**
     * Settles a shot that has been travelling for longer than its flight time and books it as a miss. Called before
     * every trigger pull; it never blocks firing, because a weapon's rate of fire is the gun's business, not the
     * aim solver's.
     */
    public void resolveExpired(long now, double distance) {
        if (this.shotTick == Long.MIN_VALUE) {
            return;
        }
        if (now - this.shotTick < flightTicks(distance)) {
            return;
        }
        this.shotTick = Long.MIN_VALUE;
        this.registerMiss();
    }

    /** How many ticks a projectile needs at this distance; used as the miss timeout. */
    public int flightTicks(double distance) {
        return Mth.clamp((int) Math.ceil(distance / BULLET_SPEED) + 5, MIN_FLIGHT_TICKS, MAX_FLIGHT_TICKS);
    }

    /** How far ahead of the target to aim, in ticks of its own movement. */
    public double leadTicks(double distance) {
        return Mth.clamp(distance / BULLET_SPEED, 0.0D, MAX_FLIGHT_TICKS);
    }

    public double pitchBias() {
        return this.pitchBias;
    }

    public double yawBias() {
        return this.yawBias;
    }

    public int misses() {
        return this.misses;
    }

    private void registerMiss() {
        this.misses++;
        this.pitchBias = Math.min(MAX_PITCH_BIAS, this.pitchBias + PITCH_STEP);
        double sweep = Math.min(MAX_YAW_BIAS, MIN_YAW_BIAS * this.misses);
        this.yawBias = (this.misses % 2 == 0 ? -1.0D : 1.0D) * sweep;
    }

    private void reset() {
        this.shotTick = Long.MIN_VALUE;
        this.misses = 0;
        this.pitchBias = 0.0D;
        this.yawBias = 0.0D;
    }
}
