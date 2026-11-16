package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class GunAim {

    private static final double BULLET_SPEED = 12.0D;
    private static final int MIN_FLIGHT_TICKS = 10;
    private static final int MAX_FLIGHT_TICKS = 60;
    private static final double PITCH_STEP = 0.4D;
    private static final double MAX_PITCH_BIAS = 8.0D;
    private static final double MAX_YAW_BIAS = 4.0D;
    private static final double MIN_YAW_BIAS = 0.3D;

    private static final double DECAY = 0.5D;

    private int targetId = -1;
    private long shotTick = Long.MIN_VALUE;
    private int misses;
    private double pitchBias;
    private double yawBias;

    public void track(LivingEntity target) {
        if (target.getId() != this.targetId) {
            this.reset();
            this.targetId = target.getId();
        }
    }

    public void onShot(LivingEntity target) {
        this.targetId = target.getId();
        this.shotTick = target.level().getGameTime();
    }

    public void onHit(LivingEntity victim) {
        if (victim.getId() != this.targetId) {
            return;
        }
        this.shotTick = Long.MIN_VALUE;
        this.misses = 0;
        this.pitchBias *= DECAY;
        this.yawBias *= DECAY;
    }

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

    public int flightTicks(double distance) {
        return Mth.clamp((int) Math.ceil(distance / BULLET_SPEED) + 5, MIN_FLIGHT_TICKS, MAX_FLIGHT_TICKS);
    }

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
