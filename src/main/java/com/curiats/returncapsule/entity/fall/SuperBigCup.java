package com.curiats.returncapsule.entity.fall;

import com.curiats.returncapsule.Config;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

 
public final class SuperBigCup {
    private static final double MIN_INCIDENCE = 5.0D;
    private static final double MAX_INCIDENCE = 85.0D;
     
    private static final double[] INCIDENCE_FALLBACKS = {15.0D, -15.0D, 30.0D, -30.0D, 45.0D, -45.0D};
    private static final int AZIMUTH_STEPS = 12;
    private static final int[] YAW_OFFSETS = {-90, -45, -20, 0, 20, 45, 90};
    private static final int[] PITCH_OFFSETS = {-45, -20, 0, 20, 45};
    private static final double CLEAR_EPSILON = 0.05D;
    private static final DustParticleOptions MARKER_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.2F);
     
    private static final int SPIRAL_DURATION = 120;
    private static final double SPIRAL_TURNS = 2.0D;
    private static final double SPIRAL_RADIUS = 50.0D;
    private static final double SPIRAL_HEIGHT = 30.0D;

    private static List<Double> incidenceSequence;
    private static int incidenceCursor;

    private final ServerLevel level;
    @Nullable
    private UUID lockedEntity;
    private Vec3 targetPoint;
    private boolean pointTarget;
     
    private final Vec3 approachOffset;
    private final double incidenceDegrees;
     
    private final double spiralAngle;
    private int spiralTicks;

    private SuperBigCup(ServerLevel level, @Nullable UUID lockedEntity, Vec3 targetPoint,
                        Vec3 approachOffset, double incidenceDegrees, boolean pointTarget) {
        this.level = level;
        this.lockedEntity = lockedEntity;
        this.targetPoint = targetPoint;
        this.approachOffset = approachOffset;
        this.incidenceDegrees = incidenceDegrees;
        this.spiralAngle = Math.atan2(approachOffset.z, approachOffset.x);
        this.pointTarget = pointTarget;
    }

     
    public static synchronized double nextIncidence() {
        if (incidenceSequence == null) {
            List<Double> list = new ArrayList<>(201);
            for (double angle = 10.0D; angle <= 30.0D + 1.0E-6D; angle += 0.1D) {
                double rounded = Math.round(angle * 10.0D) / 10.0D;
                list.add(rounded);
            }
            Collections.shuffle(list);
            incidenceSequence = List.copyOf(list);
        }
        double angle = incidenceSequence.get(incidenceCursor % incidenceSequence.size());
        incidenceCursor = (incidenceCursor + 1) % incidenceSequence.size();
        return angle;
    }

     
    public static SuperBigCup create(ServerLevel level, Vec3 targetPoint, @Nullable UUID lockedEntity,
                                     double incidenceDegrees, double playerDistance,
                                     @Nullable Vec3 playerPos,
                                     @Nullable Set<Double> reservedAngles,
                                     RandomSource random) {
        int simulation = Math.max(1, level.getServer().getPlayerList().getSimulationDistance());
        int margin = Config.BOOM_SPAWN_MARGIN_CHUNKS.get();
        double radius = Math.max(16.0D, (simulation - margin) * 16.0D);
        double baseAngle = Mth.clamp(incidenceDegrees, MIN_INCIDENCE, MAX_INCIDENCE);
        double angleRad = Math.toRadians(baseAngle);
        double cos = Math.max(0.05D, Math.cos(angleRad));
        
        
        double horizontal = Math.max(0.0D, playerDistance) + radius * cos;
        double distance = horizontal / cos;
        double baseAzimuth = playerPos == null
                ? random.nextDouble() * Math.PI * 2.0D
                : Math.atan2(playerPos.z - targetPoint.z, playerPos.x - targetPoint.x);
        Approach approach = chooseApproach(level, targetPoint, baseAngle, distance, baseAzimuth,
                reservedAngles);
        return new SuperBigCup(level, lockedEntity, targetPoint, approach.offset(), approach.angle(),
                lockedEntity == null);
    }

     
    public static SuperBigCup restore(ServerLevel level, @Nullable UUID lockedEntity,
                                      Vec3 fallbackPoint, Vec3 approachOffset, double incidenceDegrees,
                                      boolean pointTarget) {
        Vec3 point = fallbackPoint;
        if (lockedEntity != null) {
            Entity entity = level.getEntity(lockedEntity);
            if (entity != null && entity.isAlive()) {
                point = entity.getBoundingBox().getCenter();
            }
        }
        return new SuperBigCup(level, lockedEntity, point, approachOffset, incidenceDegrees, pointTarget);
    }

     
    public void update(Vec3 from) {
        
        
        
        if (this.spiralTicks < SPIRAL_DURATION) {
            this.spiralTicks++;
        }
        if (this.lockedEntity == null) {
            return;
        }
        Entity entity = this.level.getEntity(this.lockedEntity);
        if (entity == null || !entity.isAlive()) {
            this.lockedEntity = null;
            this.pointTarget = false;
            return;
        }
        this.targetPoint = entity.getBoundingBox().getCenter();
    }

    public Vec3 targetPoint() {
        return this.targetPoint;
    }

     
    public Vec3 aimPoint() {
        if (this.spiralTicks >= SPIRAL_DURATION) {
            return this.targetPoint;
        }
        double t = this.spiralTicks / (double) SPIRAL_DURATION;
        Vec3 start = this.launchPoint();
        Vec3 end = this.targetPoint;
        Vec3 delta = end.subtract(start);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double curve = Math.tan(Math.toRadians(this.incidenceDegrees)) * horizontal * 0.5D;
        double sideX = horizontal < 1.0E-4D ? 0.0D : -delta.z / horizontal;
        double sideZ = horizontal < 1.0E-4D ? 0.0D : delta.x / horizontal;
        Vec3 control = new Vec3(
                start.x + delta.x * 0.5D + sideX * curve,
                start.y + delta.y * 0.5D + Math.abs(delta.y) * 0.15D,
                start.z + delta.z * 0.5D + sideZ * curve);
        double u = 1.0D - t;
        double w0 = u * u;
        double w1 = 2.0D * u * t;
        double w2 = t * t;
        return new Vec3(
                start.x * w0 + control.x * w1 + end.x * w2,
                start.y * w0 + control.y * w1 + end.y * w2,
                start.z * w0 + control.z * w1 + end.z * w2);
    }

     
    public Vec3 launchPoint() {
        return this.targetPoint.add(this.approachOffset);
    }

    public double incidenceDegrees() {
        return this.incidenceDegrees;
    }

    @Nullable
    public UUID lockedEntity() {
        return this.lockedEntity;
    }

    public boolean hasTarget() {
        return this.lockedEntity != null || this.pointTarget;
    }

    public boolean isPointTarget() {
        return this.pointTarget;
    }

    public boolean isLockedEntity(UUID uuid) {
        return uuid.equals(this.lockedEntity);
    }

     
    public Vec3 steer(Vec3 from, Vec3 desired, @Nullable Entity source) {
        Vec3 direct = this.targetPoint.subtract(from);
        double distance = direct.length();
        if (distance < 1.0E-4D) {
            return desired;
        }
        direct = direct.scale(1.0D / distance);
        if (isClear(from, this.targetPoint, source)) {
            return desired;
        }

        double baseYaw = Math.atan2(direct.x, direct.z);
        double horiz = Math.sqrt(direct.x * direct.x + direct.z * direct.z);
        double basePitch = Math.atan2(-direct.y, horiz);
        double probe = Math.min(distance, Math.max(2.0D, Config.TERMINAL_VELOCITY.get() * 4.0D));
        Vec3 best = direct;
        double bestScore = -Double.MAX_VALUE;
        for (int yawOffset : YAW_OFFSETS) {
            for (int pitchOffset : PITCH_OFFSETS) {
                double yaw = baseYaw + Math.toRadians(yawOffset);
                double pitch = Mth.clamp(basePitch + Math.toRadians(pitchOffset),
                        -Math.PI * 0.499D, Math.PI * 0.499D);
                Vec3 dir = direction(yaw, pitch);
                double clear = clearDistance(this.level, from, from.add(dir.scale(probe)), source);
                double score = dir.dot(direct) + (clear / probe) * 0.75D;
                if (score > bestScore) {
                    bestScore = score;
                    best = dir;
                }
            }
        }
        return best;
    }

     
    public void markerParticles() {
        Vec3 point = this.targetPoint;
        double x = point.x;
        double y = point.y + 0.15D;
        double z = point.z;
        CapsuleParticles.broadcast(this.level, ParticleTypes.SMOKE,
                x, y, z, 2, 0.28D, 0.04D, 0.28D, 0.004D);
        CapsuleParticles.broadcast(this.level, MARKER_DUST,
                x, y, z, 2, 0.28D, 0.04D, 0.28D, 0.004D);
        for (int i = 0; i < 10; i++) {
            CapsuleParticles.broadcast(this.level, ParticleTypes.END_ROD,
                    x, y + 0.5D + i * 0.9D, z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
        }
    }

    private boolean isClear(Vec3 from, Vec3 to, @Nullable Entity source) {
        return clearDistance(this.level, from, to, source) >= from.distanceTo(to) - CLEAR_EPSILON;
    }

     
    private static double clearDistance(net.minecraft.world.level.Level level, Vec3 from, Vec3 to,
                                        @Nullable Entity source) {
        if (from.distanceToSqr(to) < 1.0E-6D) {
            return 0.0D;
        }
        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
        if (hit.getType() == HitResult.Type.MISS) {
            return from.distanceTo(to);
        }
        return hit.getLocation().distanceTo(from);
    }

    private static Approach chooseApproach(ServerLevel level, Vec3 target, double baseAngle,
                                           double distance, double baseAzimuth,
                                           @Nullable Set<Double> reservedAngles) {
        List<Double> angles = new ArrayList<>(INCIDENCE_FALLBACKS.length + 1);
        angles.add(baseAngle);
        for (double offset : INCIDENCE_FALLBACKS) {
            double angle = Mth.clamp(baseAngle + offset, MIN_INCIDENCE, MAX_INCIDENCE);
            if (!angles.contains(angle)) {
                angles.add(angle);
            }
        }

        Vec3 best = null;
        double bestAngle = baseAngle;
        double bestClear = -1.0D;
        for (int index = 0; index < angles.size(); index++) {
            double angle = angles.get(index);
            
            if (index > 0 && isReserved(angle, reservedAngles)) {
                continue;
            }
            for (int step = 0; step < AZIMUTH_STEPS; step++) {
                double azimuth = baseAzimuth + step * (Math.PI * 2.0D / AZIMUTH_STEPS);
                Vec3 offset = offsetFor(angle, azimuth, distance);
                Vec3 spawn = target.add(offset);
                double clear = clearDistance(level, spawn, target, null);
                if (clear >= distance - CLEAR_EPSILON) {
                    return new Approach(offset, angle);
                }
                if (clear > bestClear) {
                    bestClear = clear;
                    best = offset;
                    bestAngle = angle;
                }
            }
        }
        return new Approach(best == null ? offsetFor(baseAngle, baseAzimuth, distance) : best, bestAngle);
    }

    private static boolean isReserved(double angle, @Nullable Set<Double> reserved) {
        if (reserved == null || reserved.isEmpty()) {
            return false;
        }
        for (double candidate : reserved) {
            if (Math.abs(candidate - angle) < 1.0E-4D) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 offsetFor(double angleDegrees, double azimuth, double length) {
        double angle = Math.toRadians(angleDegrees);
        double horizontal = length * Math.cos(angle);
        double vertical = length * Math.sin(angle);
        return new Vec3(Math.cos(azimuth) * horizontal, vertical, Math.sin(azimuth) * horizontal);
    }

    private static Vec3 direction(double yaw, double pitch) {
        double cosPitch = Math.cos(pitch);
        return new Vec3(Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    private record Approach(Vec3 offset, double angle) {
    }
}
