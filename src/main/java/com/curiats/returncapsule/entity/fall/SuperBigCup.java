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

/**
 * Flight brain of the cruise bomb. It is also the only owner of the "anchor type II": the anchor
 * is not a block, it is a target point held here plus a particle marker drawn for every player.
 *
 * <p>Responsibilities:</p>
 * <ol>
 *   <li><b>Target point handling</b> — a fixed world point, or the live centre of a locked
 *       entity.</li>
 *   <li><b>Continuous locking</b> — {@link #update()} re-resolves the locked entity every tick,
 *       so the bomb follows it after it walks away.</li>
 *   <li><b>Incidence angle and spawn point</b> — the bomb appears at the edge of the server's
 *       simulation distance and flies a straight line to the target. {@link #nextIncidence()}
 *       hands out a different base angle for every bomb; if the line is obstructed the controller
 *       tries other angles and azimuths.</li>
 *   <li><b>Flexible steering</b> — a fan of directions around the direct line is sampled every
 *       tick, which allows the sharp turns needed to follow a channel to the target.</li>
 *   <li><b>Marker particles</b> — {@link #markerParticles()} is the visible anchor type II: the
 *       same smoke + dust puff as anchor type I, in white, broadcast to every player.</li>
 * </ol>
 */
public final class SuperBigCup {
    private static final double MIN_INCIDENCE = 5.0D;
    private static final double MAX_INCIDENCE = 85.0D;
    /** Extra incidence angles tried when the requested one is obstructed, as offsets in degrees. */
    private static final double[] INCIDENCE_FALLBACKS = {15.0D, -15.0D, 30.0D, -30.0D, 45.0D, -45.0D};
    private static final int AZIMUTH_STEPS = 12;
    private static final int[] YAW_OFFSETS = {-90, -45, -20, 0, 20, 45, 90};
    private static final int[] PITCH_OFFSETS = {-45, -20, 0, 20, 45};
    private static final double CLEAR_EPSILON = 0.05D;
    private static final double MARKER_RADIUS = 50.0D;
    private static final DustParticleOptions MARKER_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.2F);

    private static List<Double> incidenceSequence;
    private static int incidenceCursor;

    private final ServerLevel level;
    @Nullable
    private UUID lockedEntity;
    private Vec3 targetPoint;
    /** Offset from the live target point to the spawn point at the loaded chunk edge. */
    private final Vec3 approachOffset;
    private final double incidenceDegrees;

    private SuperBigCup(ServerLevel level, @Nullable UUID lockedEntity, Vec3 targetPoint,
                        Vec3 approachOffset, double incidenceDegrees) {
        this.level = level;
        this.lockedEntity = lockedEntity;
        this.targetPoint = targetPoint;
        this.approachOffset = approachOffset;
        this.incidenceDegrees = incidenceDegrees;
    }

    /**
     * Hands out the next incidence angle. The sequence holds every 0.1 degree from 5 to 85
     * (801 distinct values), so even a full 256 bomb queue never repeats an angle.
     */
    public static synchronized double nextIncidence() {
        if (incidenceSequence == null) {
            double base = Mth.clamp(Config.BOOM_INCIDENCE_DEGREES.get(), MIN_INCIDENCE, MAX_INCIDENCE);
            List<Double> list = new ArrayList<>(802);
            list.add(base);
            List<Double> rest = new ArrayList<>(801);
            for (double angle = MIN_INCIDENCE; angle <= MAX_INCIDENCE + 1.0E-6D; angle += 0.1D) {
                double rounded = Math.round(angle * 10.0D) / 10.0D;
                if (Math.abs(rounded - base) > 1.0E-4D) {
                    rest.add(rounded);
                }
            }
            Collections.shuffle(rest);
            list.addAll(rest);
            incidenceSequence = List.copyOf(list);
        }
        double angle = incidenceSequence.get(incidenceCursor % incidenceSequence.size());
        incidenceCursor = (incidenceCursor + 1) % incidenceSequence.size();
        return angle;
    }

    /**
     * Builds a controller for a freshly marked target. The spawn point sits inside the edge of
     * the server simulation distance, at the requested incidence angle. {@code playerDistance} is
     * the distance from the shooter to the target: it keeps the spawn inside the ticking area
     * when the target itself is already far from the player.
     */
    public static SuperBigCup create(ServerLevel level, Vec3 targetPoint, @Nullable UUID lockedEntity,
                                     double incidenceDegrees, double playerDistance,
                                     @Nullable Set<Double> reservedAngles,
                                     RandomSource random) {
        int simulation = Math.max(1, level.getServer().getPlayerList().getSimulationDistance());
        int margin = Config.BOOM_SPAWN_MARGIN_CHUNKS.get();
        double radius = Math.max(16.0D, (simulation - margin) * 16.0D);
        double distance = Math.max(16.0D, radius - Math.max(0.0D, playerDistance));
        double baseAngle = Mth.clamp(incidenceDegrees, MIN_INCIDENCE, MAX_INCIDENCE);
        Approach approach = chooseApproach(level, targetPoint, baseAngle, distance, reservedAngles, random);
        return new SuperBigCup(level, lockedEntity, targetPoint, approach.offset(), approach.angle());
    }

    /** Rebuilds a controller from saved data, keeping the previously chosen spawn offset. */
    public static SuperBigCup restore(ServerLevel level, @Nullable UUID lockedEntity,
                                      Vec3 fallbackPoint, Vec3 approachOffset, double incidenceDegrees) {
        Vec3 point = fallbackPoint;
        if (lockedEntity != null) {
            Entity entity = level.getEntity(lockedEntity);
            if (entity != null && entity.isAlive()) {
                point = entity.getBoundingBox().getCenter();
            }
        }
        return new SuperBigCup(level, lockedEntity, point, approachOffset, incidenceDegrees);
    }

    /**
     * Re-reads the locked entity. This is the "continuous lock": the target point follows the
     * entity as long as it is alive; after it dies the last known point is kept.
     */
    public void update() {
        if (this.lockedEntity == null) {
            return;
        }
        Entity entity = this.level.getEntity(this.lockedEntity);
        if (entity == null) {
            return;
        }
        if (entity.isAlive()) {
            this.targetPoint = entity.getBoundingBox().getCenter();
        } else {
            this.lockedEntity = null;
        }
    }

    public Vec3 targetPoint() {
        return this.targetPoint;
    }

    /** Spawn point on the loaded edge; it follows a moving target. */
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

    public boolean isLockedEntity(UUID uuid) {
        return uuid.equals(this.lockedEntity);
    }

    /**
     * Direct line if possible, otherwise the best direction of a fan around it. No turn rate
     * limit on purpose: the design allows sharp turns to squeeze through a channel.
     */
    public Vec3 steer(Vec3 from, Vec3 desired, @Nullable Entity source) {
        Vec3 direct = this.targetPoint.subtract(from);
        double distance = direct.length();
        if (distance < 1.0E-4D) {
            return desired;
        }
        direct = direct.scale(1.0D / distance);
        if (isClear(from, this.targetPoint, source)) {
            return direct;
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

    /**
     * The visible "anchor type II": the same smoke + dust puff as anchor type I, in white.
     * Only players within 50 blocks of the point receive it, and it is drawn at the live target
     * point so a locked entity carries it along.
     */
    public void markerParticles() {
        Vec3 point = this.targetPoint;
        double x = point.x;
        double y = point.y + 0.15D;
        double z = point.z;
        CapsuleParticles.within(this.level, MARKER_RADIUS, ParticleTypes.SMOKE,
                x, y, z, 2, 0.28D, 0.04D, 0.28D, 0.004D);
        CapsuleParticles.within(this.level, MARKER_RADIUS, MARKER_DUST,
                x, y, z, 2, 0.28D, 0.04D, 0.28D, 0.004D);
    }

    private boolean isClear(Vec3 from, Vec3 to, @Nullable Entity source) {
        return clearDistance(this.level, from, to, source) >= from.distanceTo(to) - CLEAR_EPSILON;
    }

    /** Distance travelled from {@code from} before the first collider, or the full distance. */
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
                                           double distance, @Nullable Set<Double> reservedAngles,
                                           RandomSource random) {
        double baseAzimuth = random.nextDouble() * Math.PI * 2.0D;
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
            // The requested angle is always allowed; fallbacks must not steal another bomb's angle.
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
