package com.curiats.returncapsule.compat.vehicle;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.util.List;

public final class VehicleDriverCompat {

    private static final String VEHICLE_CLASS = "org.ywzj.vehicle.entity.vehicle.AbstractVehicle";
    private static final double PROBE_RANGE = 16.0D;

    private static final double MIN_ENGAGE_RANGE = 48.0D;

    private static final double STOP_RANGE = 12.0D;

    /** How close a following vehicle closes in on the owner's trail before it stops. */
    private static final double FOLLOW_KEEP_RANGE = 8.0D;

    /** Owner distance that wakes a following vehicle up. Was a hard 24 block dead zone. */
    private static final double FOLLOW_START_RANGE = 16.0D;

    /** How many ticks behind the owner the follow point sits. */
    private static final double FOLLOW_TRAIL_TICKS = 10.0D;

    /** Heading error above which the vehicle must roll forward to turn instead of pivoting. */
    private static final double TURN_FIRST_DEGREES = 35.0D;

    /** A target closer than this makes the hull back away instead of sitting in its face. */
    private static final double KITE_RANGE = 22.0D;

    private static final double KITE_DISTANCE = 30.0D;

    /** Ticks a chosen steering side is held, so the wheel does not twitch left-right every tick. */
    private static final int STEER_HOLD = 6;

    /** Reversing the wheel is only allowed past this heading error, straight-line jitter stays out of it. */
    private static final double STEER_HYSTERESIS = 20.0D;

    /** Enemies are counted around the owner, never around the helicopter, or it would fly off chasing spawns. */
    private static final double ENEMY_RANGE = 64.0D;

    private static final double ESCORT_RADIUS = 20.0D;

    private static final double ESCORT_ALTITUDE = 14.0D;

    private static final double ORBIT_STEP = 0.02D;

    private static final double CRUISE_PITCH = 12.0D;

    private static final double LAND_SEARCH_RADIUS = 64.0D;

    private static final double LAND_ALTITUDE = 12.0D;

    private static final double MAX_SINK = 0.25D;

    private static final java.util.Map<Integer, Double> ORBIT = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, net.minecraft.core.BlockPos> LANDING_SPOT =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final String CONTROL_CLASS = "org.ywzj.vehicle.vehicle.control.ControlUnit";

    private static boolean resolved;
    private static boolean broken;
    private static Field controlUnitField;
    private static Field forwardField;
    private static Field backwardField;
    private static Field leftField;
    private static Field rightField;
    private static Field handbrakeField;
    private static Field leftYawField;
    private static Field rightYawField;
    private static Field yRotField;
    private static Field yRotKeepField;
    private static Field downField;
    private static Field xRotField;
    private static Field xRotKeepField;
    private static Field seatsField;
    private static java.lang.reflect.Method toggleEngine;
    private static java.lang.reflect.Method isEngineOn;

    private static final java.util.Map<Integer, Integer> FLANK_ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, Integer> FLANK_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, net.minecraft.world.phys.Vec3> FLANK_AIM = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Integer> STUCK_TICKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, Integer> THROTTLE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Integer> RECOVERY = new java.util.concurrent.ConcurrentHashMap<>();

    /** Rolling hysteresis so a following vehicle does not stutter between throttle and handbrake. */
    private static final java.util.Map<Integer, Boolean> ROLLING = new java.util.concurrent.ConcurrentHashMap<>();

    /** Wall avoidance: panic distance, the angle searched either side, and how long a side choice is held. */
    private static final double WALL_PANIC = 10.0D;

    private static final float DETOUR_ANGLE = 60.0F;

    private static final int DETOUR_HOLD = 80;

    private static final double DETOUR_COMMIT_DEGREES = 60.0D;

    private static final int DETOUR_CYCLE = 40;

    private static final int DETOUR_HALF = 20;

    private static final java.util.Map<Integer, Integer> DETOUR = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Double> DETOUR_YAW = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Integer> DETOUR_TICKS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Integer> UNSTICK = new java.util.concurrent.ConcurrentHashMap<>();

    /** Ticks of "throttle but no movement" before the AI treats the vehicle as wedged on something thin. */
    private static final int JAM_TICKS = 8;

    private static final java.util.Map<Integer, Integer> JAM = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether the last tick asked for forward; only forward throttle that fails to move counts as jammed. */
    private static final java.util.Map<Integer, Boolean> LAST_FORWARD = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Integer> STEER_LATCH = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Integer> LAST_STEER = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.AtomicLong DRIVE_LOGS = new java.util.concurrent.atomic.AtomicLong();

    private static final int RECOVERY_TICKS = 60;

    private static final int RECOVERY_PHASE = 10;

    private static final java.util.Map<Integer, int[]> REPLAY = new java.util.concurrent.ConcurrentHashMap<>();

    private VehicleDriverCompat() {
    }

    public static void tick(NeutralHumanoid humanoid) {
        if (humanoid.level().isClientSide || humanoid.isDowned()) {
            return;
        }
        Entity vehicle = humanoid.getVehicle();
        if (vehicle == null) {
            humanoid.setDrivingVehicle(false);
            ROLLING.remove(humanoid.getId());
            DETOUR.remove(humanoid.getId());
            DETOUR_TICKS.remove(humanoid.getId());
            DETOUR_YAW.remove(humanoid.getId());
            JAM.remove(humanoid.getId());
            LAST_FORWARD.remove(humanoid.getId());
            VehiclePathPlanner.forget(humanoid.getId());
            return;
        }
        if (!resolve()) {
            return;
        }
        try {
            List<?> seats = (List<?>) seatsField.get(vehicle);
            if (seats == null || seats.isEmpty()) {
                return;
            }
            Object seatZero = seats.get(0);
            java.lang.reflect.Field passengerIdField = seatZero.getClass().getField("passengerId");
            Object riderId = passengerIdField.get(seatZero);
            boolean drives = riderId instanceof Integer id && id == humanoid.getId();
            humanoid.setDrivingVehicle(drives);
            Object control = controlUnitField.get(vehicle);
            if (control == null) {
                return;
            }
            if (!drives) {
                return;
            }
            if (isFixedWing(vehicle)) {
                // Ground controls on a fixed wing aircraft are worse than no controls at all: clamp the stick.
                ROLLING.remove(humanoid.getId());
                parkFlightControls(control);
                return;
            }
            if (isRotaryWing(vehicle)) {
                ROLLING.remove(humanoid.getId());
                tickRotorWing(humanoid, vehicle, control);
                return;
            }
            LivingEntity target = humanoid.getTarget();
            net.minecraft.world.entity.player.Player owner = humanoid.level().getNearestPlayer(humanoid, 256.0D);
            boolean ownerAboard = owner != null && owner.getVehicle() == vehicle;
            net.minecraft.world.phys.Vec3 aim = null;
            if (target != null) {
                aim = target.position();
            } else if (owner != null && !ownerAboard) {
                // Aim at the owner's trail rather than the owner: chasing the live position made the vehicle
                // overshoot and brake, and the old 24 block gate cut driving off exactly inside that band.
                net.minecraft.world.phys.Vec3 trail = owner.position()
                        .subtract(owner.getDeltaMovement().scale(FOLLOW_TRAIL_TICKS));
                if (humanoid.distanceToSqr(owner) > FOLLOW_START_RANGE * FOLLOW_START_RANGE) {
                    aim = trail;
                }
            }
            if (aim == null) {
                ROLLING.remove(humanoid.getId());
                DETOUR.remove(humanoid.getId());
                DETOUR_TICKS.remove(humanoid.getId());
                DETOUR_YAW.remove(humanoid.getId());
                JAM.remove(humanoid.getId());
                VehiclePathPlanner.forget(humanoid.getId());
                stopAll(control);
                return;
            }
            ensureEngine(vehicle);
            if (target != null && !lineClear(vehicle, target)) {
                net.minecraft.world.phys.Vec3 flank = flankAim(humanoid, vehicle, target);
                if (flank == null) {
                    humanoid.setTarget(null);
                    stopAll(control);
                    return;
                }
                aim = flank;
            }
            aim = cornerCorridorShift(vehicle, aim);
            net.minecraft.world.phys.Vec3 order = VehicleOrders.take(vehicle);
            if (order != null) {
                // The gunner found a firing spot; drive there first, weapons keep tracking meanwhile.
                aim = order;
            }
            net.minecraft.world.phys.Vec3 replayAim = replayAim(humanoid, vehicle, owner, forwardSpeed(vehicle), target != null);
            if (replayAim != null) {
                aim = replayAim;
            }
            if (target != null && vehicle.distanceTo(target) < KITE_RANGE) {
                // Too close: put distance between the hull and the target, then let the cannon work again.
                net.minecraft.world.phys.Vec3 away = vehicle.position().subtract(target.position());
                away = new net.minecraft.world.phys.Vec3(away.x, 0.0D, away.z);
                if (away.lengthSqr() > 1.0E-3D) {
                    aim = vehicle.position().add(away.normalize().scale(KITE_DISTANCE));
                }
            }
            // A* in combat too: the reactive probes cannot see round a corner, a grid search can.
            net.minecraft.world.phys.Vec3 routed = VehiclePathPlanner.route(vehicle, aim);
            if (routed != null) {
                aim = routed;
            }
            double yawError = yawErrorTo(vehicle, aim);
            double rawDistance = vehicle.position().distanceTo(aim);
            // Follow mode wants to close the gap, not to hold a fighting standoff: subtracting the full body clearance
            // parked the vehicle 15+ blocks behind the player, which read as "it cannot keep up".
            double clearance = target != null ? clearanceTo(vehicle, target, owner) : 2.0D;
            double distance = rawDistance - clearance;
            double keep = target != null ? Math.max(MIN_ENGAGE_RANGE, weaponRange(vehicle))
                    : FOLLOW_KEEP_RANGE;
            boolean tracked = isTracked(vehicle);
            double frontClear = cornerClearance(vehicle, yawError);
            double safeFront = vehicle.getBbWidth() * 0.5D + 2.0D;
            boolean groundAhead = groundAhead(vehicle);
            double aheadClear = clearanceAlong(vehicle, vehicle.getYRot());
            double groundSpeed = vehicle.getDeltaMovement().horizontalDistance();
            // Braking distance gate, capped: the raw v^2 figure demanded 20+ blocks at walking pace on a heavy hull and
            // throttled it to a crawl even down an empty street.
            double stopping = Math.min(12.0D, groundSpeed * groundSpeed / 0.05D + vehicle.getBbWidth());
            // Traversability, not a ray probe: whether the hull's own box fits ahead is what decides a brake,
            // and a ray can miss a low kerb or clip a thin post the box would have cleared.
            boolean mustBrake = frontBlocked(vehicle, Math.min(6.0D, stopping + 1.0D));
            double stopAt = ROLLING.getOrDefault(humanoid.getId(), false) ? keep - 3.0D : keep;
            double turnLimit = Math.max(25.0D, 70.0D - groundSpeed * 18.0D);
            boolean forward = !mustBrake && distance > stopAt && Math.abs(yawError) < turnLimit
                    && Math.min(frontClear, aheadClear) > safeFront && groundAhead;
            boolean backward = mustBrake || (target != null ? distance < STOP_RANGE : distance < 6.0D);
            // Jam = forward was asked for and the hull did not move. Reverse and pivot phases must not count, otherwise
            // a stuck tracked vehicle kept refreshing its own jam timer while turning on the spot.
            boolean askedForward = LAST_FORWARD.getOrDefault(humanoid.getId(), false);
            if (askedForward && groundSpeed < 0.05D) {
                JAM.merge(humanoid.getId(), 1, Integer::sum);
            } else if (groundSpeed > 0.15D) {
                JAM.remove(humanoid.getId());
            }
            int jamTicks = JAM.getOrDefault(humanoid.getId(), 0);
            if (jamTicks == JAM_TICKS) {
                // Baritone's executor drops a path that stopped making progress; do the same, otherwise the hull keeps
                // following a cached route that already proved impossible.
                VehiclePathPlanner.forget(vehicle.getId());
            }
            int steerSide = 0;
            int detourTicks = DETOUR_TICKS.getOrDefault(humanoid.getId(), 0);
            boolean needDetour = aheadClear < WALL_PANIC || jamTicks >= JAM_TICKS;
            if (needDetour) {
                if (detourTicks <= 0) {
                    // New episode: choose the side once and keep it. Re-picking every cycle used to flip the steering
                    // every time the hull turned past the commit angle, which is what kept it pirouetting.
                    double leftRoom = clearanceAlong(vehicle, vehicle.getYRot() - DETOUR_ANGLE);
                    double rightRoom = clearanceAlong(vehicle, vehicle.getYRot() + DETOUR_ANGLE);
                    DETOUR.put(humanoid.getId(), leftRoom >= rightRoom ? -1 : 1);
                    DETOUR_YAW.put(humanoid.getId(), (double) vehicle.getYRot());
                    detourTicks = DETOUR_HOLD;
                }
                detourTicks--;
                DETOUR_TICKS.put(humanoid.getId(), detourTicks);
                int side = DETOUR.getOrDefault(humanoid.getId(), -1);
                if (tracked) {
                    // Roll forward as soon as the nose is clear, instead of pivoting without throttle. With no
                    // throttle the old code never moved, and its "is it moving again" exit could never trigger.
                    forward = !frontBlocked(vehicle, 2.5D) && Math.abs(yawError) < 45.0D;
                    backward = false;
                    steerSide = forward ? 0 : side;
                } else {
                    int phase = (int) (UNSTICK.merge(humanoid.getId(), 1, Integer::sum) % DETOUR_CYCLE);
                    boolean reversePhase = phase < DETOUR_HALF;
                    forward = !reversePhase;
                    backward = reversePhase;
                    steerSide = reversePhase ? -side : side;
                }
                if (detourTicks <= 0) {
                    DETOUR.remove(humanoid.getId());
                    DETOUR_TICKS.remove(humanoid.getId());
                    DETOUR_YAW.remove(humanoid.getId());
                    UNSTICK.remove(humanoid.getId());
                }
            } else {
                DETOUR.remove(humanoid.getId());
                DETOUR_TICKS.remove(humanoid.getId());
                DETOUR_YAW.remove(humanoid.getId());
                UNSTICK.remove(humanoid.getId());
            }
            // Wheeled and tracked hulls turn differently. A wheeled vehicle only changes heading while it rolls, so a
            // large error has to be solved with throttle; a tracked one pivots on the spot and must not be forced to
            // drive just to turn (that is what dragged it into circles).
            if (!tracked && Math.abs(yawError) > TURN_FIRST_DEGREES && distance > 2.0D
                    && frontClear > safeFront && groundAhead) {
                forward = true;
                backward = false;
            }
            if (entityAhead(vehicle)) {
                // Something alive is in front: brake, do not swerve. Swerving at a close enemy is what produced the
                // endless spin, and driving through friendlies is not acceptable either.
                forward = false;
                backward = false;
                steerSide = 0;
            }
            boolean complexTerrain = steerSide == 0 && (frontClear < 5.0D || !groundAhead
                    || Math.abs(yawError) > 25.0D || sideTight(vehicle));
            if (complexTerrain) {
                int throttle = THROTTLE.getOrDefault(humanoid.getId(), 0) + 1;
                THROTTLE.put(humanoid.getId(), throttle);
                if (!isTracked(vehicle) && forward && (throttle % 4) >= 2) {
                    forward = false;
                    backward = false;
                    handbrakeField.setBoolean(control, true);
                }
            } else {
                THROTTLE.remove(humanoid.getId());
            }
            int recovery = RECOVERY.getOrDefault(humanoid.getId(), 0);
            if (recovery > 0 && steerSide == 0) {
                RECOVERY.put(humanoid.getId(), recovery - 1);
                int phase = (RECOVERY_TICKS - recovery) / RECOVERY_PHASE % 4;
                if (tracked) {
                    forward = false;
                    backward = false;
                } else {
                    forward = phase >= 2;
                    backward = phase < 2;
                }
                boolean steerLeft = phase % 2 == 0;
                handbrakeField.setBoolean(control, false);
                forwardField.setBoolean(control, forward);
                backwardField.setBoolean(control, backward);
                leftYawField.setBoolean(control, steerLeft);
                rightYawField.setBoolean(control, !steerLeft);
                yRotKeepField.setBoolean(control, false);
                if (recovery <= 1) {
                    RECOVERY.remove(humanoid.getId());
                }
                return;
            }
            if (forward && forwardSpeed(vehicle) < 0.03D) {
                int stall = STUCK_TICKS.getOrDefault(humanoid.getId(), 0) + 1;
                STUCK_TICKS.put(humanoid.getId(), stall);
                if (stall > 30) {
                    STUCK_TICKS.put(humanoid.getId(), 0);
                    RECOVERY.put(humanoid.getId(), RECOVERY_TICKS);
                    return;
                }
            } else if (forward) {
                STUCK_TICKS.remove(humanoid.getId());
            }
            if (target != null && steerSide == 0) {
                double[] pitch = pitchWindow(vehicle, target);
                if (pitch != null) {
                    if (pitch[0] < pitch[1]) {
                        backward = true;
                        forward = false;
                    } else if (pitch[0] > pitch[2]) {
                        forward = true;
                        backward = false;
                    }
                }
            }
            LAST_FORWARD.put(humanoid.getId(), forward);
            boolean moving = forward || backward;
            boolean pivoting = tracked && !moving && (Math.abs(yawError) > 8.0D || steerSide != 0);
            // Hold the chosen side for a few ticks (Automobility's steering is a continuous input, this hull only
            // has a boolean wheel). A reversal needs a clearly larger error, which removes the left-right twitch that
            // was visible in the drive trace.
            int desiredSide = steerSide != 0 ? Integer.signum(steerSide)
                    : (moving || pivoting) && Math.abs(yawError) > 4.0D ? (yawError < 0.0D ? -1 : 1) : 0;
            int heldSide = LAST_STEER.getOrDefault(humanoid.getId(), 0);
            int latch = STEER_LATCH.getOrDefault(humanoid.getId(), 0);
            if (latch > 0 && desiredSide != 0 && desiredSide != heldSide
                    && Math.abs(yawError) < STEER_HYSTERESIS) {
                desiredSide = heldSide;
            }
            if (desiredSide != 0 && desiredSide != heldSide) {
                LAST_STEER.put(humanoid.getId(), desiredSide);
                STEER_LATCH.put(humanoid.getId(), STEER_HOLD);
            } else if (latch > 0) {
                STEER_LATCH.put(humanoid.getId(), latch - 1);
            }
            boolean left = desiredSide < 0;
            boolean right = desiredSide > 0;
            boolean handbrake = !moving && !pivoting;
            if (humanoid.tickCount % 20 == 0 && DRIVE_LOGS.incrementAndGet() <= 600) {
                com.curiats.returncapsule.ReturnCapsuleMod.LOGGER.info(
                        "[drive] {} tracked={} yawErr={} dist={} fwd={} back={} steer={} jam={} detour={} blocked={}",
                        vehicle.getType().toString(), tracked, String.format("%.1f", yawError),
                        String.format("%.1f", distance), forward, backward, steerSide, jamTicks, detourTicks,
                        frontBlocked(vehicle, 3.0D));
            }
            if (moving) {
                ROLLING.put(humanoid.getId(), true);
            } else {
                ROLLING.remove(humanoid.getId());
            }
            forwardField.setBoolean(control, forward);
            backwardField.setBoolean(control, backward);
            leftField.setBoolean(control, left);
            rightField.setBoolean(control, right);
            handbrakeField.setBoolean(control, handbrake);
            float desiredYaw = (float) Math.toDegrees(Math.atan2(-(aim.x - vehicle.getX()),
                    aim.z - vehicle.getZ()));
            leftYawField.setBoolean(control, false);
            rightYawField.setBoolean(control, false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
        }
    }

    private static double[] pitchWindow(Entity vehicle, LivingEntity target) {
        for (Object unit : com.curiats.returncapsule.compat.vehicle.VehicleAim.seatUnits(vehicle)) {
            net.minecraft.world.phys.Vec3 origin = com.curiats.returncapsule.compat.vehicle.VehicleAim.unitPosition(unit);
            if (origin == null) {
                continue;
            }
            float required = com.curiats.returncapsule.compat.vehicle.VehicleAim.requiredPitch(unit,
                    target.getEyePosition());
            float min = com.curiats.returncapsule.compat.vehicle.VehicleAim.minPitch(unit);
            float max = com.curiats.returncapsule.compat.vehicle.VehicleAim.maxPitch(unit);
            return new double[]{required, min, max};
        }
        return null;
    }

    /** Climb ability: a tracked hull takes a two block kerb, a wheeled one a single block. */
    private static double stepHeight(Entity vehicle) {
        return isTracked(vehicle) ? 2.0D : 1.0D;
    }

    /**
     * A box just in front of the hull, the same shape the reference autopilot uses: as wide as the hull, two blocks
     * down and four up, centred a little over one hull width ahead.
     */
    private static boolean entityAhead(Entity vehicle) {
        net.minecraft.world.phys.Vec3 look = vehicle.getLookAngle();
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 1.0E-6D) {
            return false;
        }
        forward = forward.normalize();
        double width = 0.8D * vehicle.getBbWidth();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                -width, -2.0D, -width, width, 4.0D, width)
                .move(vehicle.position().add(forward.scale(1.0D + 1.75D * width)));
        for (net.minecraft.world.entity.LivingEntity other
                : vehicle.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (other == vehicle || other.getVehicle() == vehicle || other.isSpectator()) {
                continue;
            }
            return true;
        }
        for (net.minecraft.world.entity.Entity other
                : vehicle.level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
            if (other == vehicle || other.getVehicle() == vehicle) {
                continue;
            }
            // Another hull is not a LivingEntity, and driving through one is as bad as driving through a mob.
            if (other.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("vehicle")) {
                return true;
            }
        }
        return false;
    }

    /** True when the hull's own collision box does not fit a short roll forward. */
    private static boolean frontBlocked(Entity vehicle, double distance) {
        double yaw = Math.toRadians(vehicle.getYRot());
        double dx = -Math.sin(yaw) * distance;
        double dz = Math.cos(yaw) * distance;
        net.minecraft.world.phys.AABB box = vehicle.getBoundingBox().move(dx, 0.0D, dz);
        return VehicleTerrain.boxBlocked(vehicle.level(), box, stepHeight(vehicle));
    }

    private static double forwardSpeed(Entity vehicle) {
        net.minecraft.world.phys.Vec3 delta = vehicle.getDeltaMovement();
        return Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    }

    private static net.minecraft.world.phys.Vec3 replayAim(NeutralHumanoid humanoid, Entity vehicle,
            net.minecraft.world.entity.player.Player owner, double speed, boolean fighting) {
        int[] state = REPLAY.getOrDefault(humanoid.getId(), new int[]{-1, 0});
        if (state[0] >= 0) {
            return advanceReplay(humanoid, vehicle, owner, state);
        }
        if (fighting || owner == null || speed > 0.05D) {
            STUCK_TICKS.remove(humanoid.getId());
            return null;
        }
        int stuck = STUCK_TICKS.getOrDefault(humanoid.getId(), 0) + 1;
        STUCK_TICKS.put(humanoid.getId(), stuck);
        if (stuck < 60) {
            return null;
        }
        if (!ownerFitsLikeUs(owner, vehicle)) {
            STUCK_TICKS.put(humanoid.getId(), 0);
            return null;
        }
        java.util.List<net.minecraft.world.phys.Vec3> path =
                com.curiats.returncapsule.event.PlayerTrail.path(owner);
        int start = com.curiats.returncapsule.event.PlayerTrail.nearestIndex(path, vehicle.position());
        if (start < 0 || path.size() - start < 4) {
            STUCK_TICKS.put(humanoid.getId(), 0);
            return null;
        }
        REPLAY.put(humanoid.getId(), new int[]{start, 0});
        STUCK_TICKS.put(humanoid.getId(), 0);
        return advanceReplay(humanoid, vehicle, owner, REPLAY.get(humanoid.getId()));
    }

    private static net.minecraft.world.phys.Vec3 advanceReplay(NeutralHumanoid humanoid, Entity vehicle,
            net.minecraft.world.entity.player.Player owner, int[] state) {
        if (owner == null) {
            REPLAY.remove(humanoid.getId());
            return null;
        }
        java.util.List<net.minecraft.world.phys.Vec3> path =
                com.curiats.returncapsule.event.PlayerTrail.path(owner);
        state[1]++;
        if (state[1] > 240 || state[0] >= path.size()) {
            REPLAY.remove(humanoid.getId());
            return null;
        }
        net.minecraft.world.phys.Vec3 point = path.get(state[0]);
        if (vehicle.position().distanceTo(point) < 3.0D) {
            state[0]++;
            if (state[0] >= path.size()) {
                REPLAY.remove(humanoid.getId());
                return null;
            }
            point = path.get(state[0]);
        }
        return point;
    }

    private static boolean ownerFitsLikeUs(net.minecraft.world.entity.player.Player owner, Entity vehicle) {
        Entity mount = owner.getVehicle();
        if (mount == null) {
            return true;
        }
        double widthGap = vehicle.getBbWidth() - mount.getBbWidth();
        double heightGap = vehicle.getBbHeight() - mount.getBbHeight();
        return widthGap < 1.0D && heightGap < 1.0D;
    }

    private static net.minecraft.world.phys.Vec3 cornerCorridorShift(Entity vehicle, net.minecraft.world.phys.Vec3 aim) {
        net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(
                aim.x - vehicle.getX(), 0.0D, aim.z - vehicle.getZ());
        double length = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (length < 1.0E-3D) {
            return aim;
        }
        dir = new net.minecraft.world.phys.Vec3(dir.x / length, 0.0D, dir.z / length);
        double px = -dir.z;
        double pz = dir.x;
        net.minecraft.world.phys.AABB box = vehicle.getBoundingBox();
        double step = stepHeight(vehicle);
        double right = VehicleTerrain.clearance(vehicle.level(), box,
                new net.minecraft.world.phys.Vec3(-px, 0.0D, -pz), PROBE_RANGE, step);
        double left = VehicleTerrain.clearance(vehicle.level(), box,
                new net.minecraft.world.phys.Vec3(px, 0.0D, pz), PROBE_RANGE, step);
        double shift = Math.max(-2.5D, Math.min(2.5D, (right - left) * 0.25D));
        return aim.add(px * shift, 0.0D, pz * shift);
    }

    private static boolean isTracked(Entity vehicle) {
        for (Class<?> type = vehicle.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().contains("Tracked")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRotaryWing(Entity vehicle) {
        return superclassNamed(vehicle, "RotaryWing");
    }

    private static boolean isFixedWing(Entity vehicle) {
        return superclassNamed(vehicle, "FixedWing");
    }

    private static boolean superclassNamed(Entity vehicle, String needle) {
        for (Class<?> type = vehicle.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clamps every stick for an aircraft the AI deliberately does not fly. {@code stopAll} is not usable here: it
     * raises {@code up}, which on a flight vehicle is the collective (climb), while on a ground vehicle it is the
     * handbrake.
     */
    private static void parkFlightControls(Object control) throws IllegalAccessException {
        forwardField.setBoolean(control, false);
        backwardField.setBoolean(control, false);
        leftField.setBoolean(control, false);
        rightField.setBoolean(control, false);
        handbrakeField.setBoolean(control, false);
        if (downField != null) {
            downField.setBoolean(control, false);
        }
        yRotKeepField.setBoolean(control, true);
        if (xRotKeepField != null) {
            xRotKeepField.setBoolean(control, true);
        }
    }

    /**
     * Helicopter behaviour: guard the player while enemies are in sight from the sky, land when the sky is clear.
     *
     * <p>Rotor wing vehicles read the control unit with completely different meanings: {@code up}/{@code down} are
     * the collective (climb and sink), while the ground vehicle reads {@code up} as the handbrake. Attitude is
     * commanded through {@code xRot}/{@code yRot}: the mod's own stabilisers turn the hull toward {@code yRot} and
     * level the nose at {@code xRot}, so the AI never touches roll or rudder. It never flies a fixed wing aircraft
     * at all.</p>
     */
    private static void tickRotorWing(NeutralHumanoid humanoid, Entity vehicle, Object control)
            throws IllegalAccessException {
        centreSticks(control);
        if (downField == null) {
            return;
        }
        net.minecraft.world.entity.player.Player owner = vehicle.level().getNearestPlayer(vehicle, 256.0D);
        LivingEntity enemy = enemyVisibleFromSky(humanoid, vehicle, owner);
        if (enemy != null) {
            escortPlayer(vehicle, owner, control);
        } else {
            landNearPlayer(vehicle, owner, control);
        }
    }

    private static void centreSticks(Object control) throws IllegalAccessException {
        forwardField.setBoolean(control, false);
        backwardField.setBoolean(control, false);
        leftField.setBoolean(control, false);
        rightField.setBoolean(control, false);
        handbrakeField.setBoolean(control, false);
        leftYawField.setBoolean(control, false);
        rightYawField.setBoolean(control, false);
        yRotKeepField.setBoolean(control, false);
        if (xRotKeepField != null) {
            xRotKeepField.setBoolean(control, false);
        }
        if (downField != null) {
            downField.setBoolean(control, false);
        }
    }

    /**
     * An enemy counts when it sits inside the player's 64 block bubble and a straight line from 40 blocks above it
     * is not blocked, i.e. it is actually engageable from the air. Enemies in caves or under a canopy never pull the
     * helicopter away from its owner.
     */
    private static LivingEntity enemyVisibleFromSky(NeutralHumanoid humanoid, Entity vehicle,
                                                    net.minecraft.world.entity.player.Player owner) {
        net.minecraft.world.phys.Vec3 center = owner != null ? owner.position() : vehicle.position();
        for (LivingEntity candidate : vehicle.level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(ENEMY_RANGE),
                entity -> entity instanceof net.minecraft.world.entity.monster.Enemy && entity.isAlive())) {
            net.minecraft.world.phys.Vec3 fromSky = candidate.position().add(0.0D, 40.0D, 0.0D);
            if (com.curiats.returncapsule.entity.humanoid.HumanoidSight.canSee(
                    vehicle.level(), vehicle, fromSky, candidate.getEyePosition())) {
                return candidate;
            }
        }
        return null;
    }

    /** Slow orbit around the owner, 20 blocks out and low enough to keep the gunners in range. */
    private static void escortPlayer(Entity vehicle, net.minecraft.world.entity.player.Player owner, Object control)
            throws IllegalAccessException {
        double phase = ORBIT.merge(vehicle.getId(), 0.0D, (old, step) -> old + ORBIT_STEP);
        double centerX = owner != null ? owner.getX() : vehicle.getX();
        double centerZ = owner != null ? owner.getZ() : vehicle.getZ();
        double targetX = centerX + Math.cos(phase) * ESCORT_RADIUS;
        double targetZ = centerZ + Math.sin(phase) * ESCORT_RADIUS;
        float heading = headingTo(vehicle, targetX, targetZ);
        if (blockedAhead(vehicle, heading)) {
            heading += 55.0F;
            setPitch(control, 0.0D);
        } else {
            setPitch(control, CRUISE_PITCH);
        }
        setHeading(control, heading);
        holdAltitude(control, vehicle, surfaceY(vehicle, targetX, targetZ) + ESCORT_ALTITUDE);
    }

    private static void landNearPlayer(Entity vehicle, net.minecraft.world.entity.player.Player owner, Object control)
            throws IllegalAccessException {
        net.minecraft.core.BlockPos pad = LANDING_SPOT.get(vehicle.getId());
        if (pad == null || !padValid(vehicle.level(), pad)) {
            pad = findPad(vehicle, owner);
            if (pad == null) {
                setPitch(control, 0.0D);
                holdAltitude(control, vehicle,
                        surfaceY(vehicle, vehicle.getX(), vehicle.getZ()) + ESCORT_ALTITUDE);
                return;
            }
            LANDING_SPOT.put(vehicle.getId(), pad);
        }
        double dx = pad.getX() + 0.5D - vehicle.getX();
        double dz = pad.getZ() + 0.5D - vehicle.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat > 4.0D) {
            setHeading(control, headingTo(vehicle, pad.getX() + 0.5D, pad.getZ() + 0.5D));
            setPitch(control, CRUISE_PITCH);
            holdAltitude(control, vehicle, pad.getY() + LAND_ALTITUDE);
            return;
        }
        setPitch(control, 0.0D);
        setHeading(control, vehicle.getYRot());
        if (vehicle.onGround()) {
            centreSticks(control);
            LANDING_SPOT.remove(vehicle.getId());
            ORBIT.remove(vehicle.getId());
            return;
        }
        double sink = -vehicle.getDeltaMovement().y;
        if (sink > MAX_SINK) {
            handbrakeField.setBoolean(control, true);
        } else {
            downField.setBoolean(control, true);
        }
    }

    private static void setHeading(Object control, float yaw) throws IllegalAccessException {
        yRotField.setFloat(control, yaw);
        yRotKeepField.setBoolean(control, false);
        leftYawField.setBoolean(control, false);
        rightYawField.setBoolean(control, false);
    }

    /** Positive {@code xRot} is nose down in Minecraft, which is what makes the rotor disc pull forward. */
    private static void setPitch(Object control, double noseDownDegrees) {
        if (xRotField == null) {
            return;
        }
        try {
            if (xRotKeepField != null) {
                xRotKeepField.setBoolean(control, false);
            }
            xRotField.setFloat(control, (float) noseDownDegrees);
        } catch (IllegalAccessException ignored) {
            // the vehicle keeps its current attitude
        }
    }

    private static void holdAltitude(Object control, Entity vehicle, double targetY) throws IllegalAccessException {
        double dy = targetY - vehicle.getY();
        double vy = vehicle.getDeltaMovement().y;
        if (dy > 1.5D || vy < -0.10D) {
            handbrakeField.setBoolean(control, true);
        } else if (dy < -1.5D || vy > 0.10D) {
            downField.setBoolean(control, true);
        }
    }

    private static float headingTo(Entity vehicle, double x, double z) {
        double dx = x - vehicle.getX();
        double dz = z - vehicle.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static boolean blockedAhead(Entity vehicle, float heading) {
        double radians = Math.toRadians(heading);
        net.minecraft.world.phys.Vec3 from = vehicle.position().add(0.0D, 0.5D, 0.0D);
        net.minecraft.world.phys.Vec3 to = from.add(-Math.sin(radians) * PROBE_RANGE, 0.0D,
                Math.cos(radians) * PROBE_RANGE);
        net.minecraft.world.phys.BlockHitResult hit = vehicle.level().clip(
                new net.minecraft.world.level.ClipContext(from, to,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, vehicle));
        return hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static double surfaceY(Entity vehicle, double x, double z) {
        net.minecraft.core.BlockPos pos = vehicle.level().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new net.minecraft.core.BlockPos((int) Math.floor(x), 0, (int) Math.floor(z)));
        return pos.getY();
    }

    private static net.minecraft.core.BlockPos findPad(Entity vehicle,
                                                       net.minecraft.world.entity.player.Player owner) {
        net.minecraft.world.level.Level level = vehicle.level();
        net.minecraft.core.BlockPos center = owner != null ? owner.blockPosition() : vehicle.blockPosition();
        for (int radius = 8; radius <= (int) LAND_SEARCH_RADIUS; radius += 4) {
            for (int angle = 0; angle < 360; angle += 30) {
                int dx = (int) Math.round(Math.cos(Math.toRadians(angle)) * radius);
                int dz = (int) Math.round(Math.sin(Math.toRadians(angle)) * radius);
                net.minecraft.core.BlockPos surface = level.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        center.offset(dx, 0, dz));
                if (padValid(level, surface)) {
                    return surface;
                }
            }
        }
        return null;
    }

    private static boolean padValid(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pad) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                net.minecraft.core.BlockPos feet = pad.offset(dx, 0, dz);
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
                    return false;
                }
                net.minecraft.core.BlockPos floor = feet.below();
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                        || !level.getBlockState(floor).getFluidState().isEmpty()) {
                    return false;
                }
                int height = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        feet.getX(), feet.getZ());
                if (Math.abs(height - pad.getY()) > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean sideTight(Entity vehicle) {
        net.minecraft.world.phys.Vec3 look = vehicle.getLookAngle();
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z).normalize();
        double px = -forward.z;
        double pz = forward.x;
        net.minecraft.world.phys.AABB box = vehicle.getBoundingBox();
        double step = stepHeight(vehicle);
        double right = VehicleTerrain.clearance(vehicle.level(), box,
                new net.minecraft.world.phys.Vec3(px, 0.0D, pz), 8.0D, step);
        double left = VehicleTerrain.clearance(vehicle.level(), box,
                new net.minecraft.world.phys.Vec3(-px, 0.0D, -pz), 8.0D, step);
        return left + right < 16.0D;
    }

    private static boolean groundAhead(Entity vehicle) {
        net.minecraft.world.phys.Vec3 look = vehicle.getLookAngle();
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z).normalize();
        net.minecraft.world.phys.AABB box = vehicle.getBoundingBox();
        for (double distance = 3.0D; distance <= 9.0D; distance += 3.0D) {
            net.minecraft.world.phys.AABB shifted = box.move(forward.x * distance, 0.0D, forward.z * distance);
            if (!VehicleTerrain.groundUnder(vehicle.level(), shifted, 2.0D)) {
                return false;
            }
        }
        return true;
    }

    private static double cornerClearance(Entity vehicle, double yawError) {
        net.minecraft.world.phys.Vec3 look = vehicle.getLookAngle();
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z);
        if (Math.abs(yawError) > 20.0D) {
            return PROBE_RANGE;
        }
        return VehicleTerrain.clearance(vehicle.level(), vehicle.getBoundingBox(), forward, PROBE_RANGE,
                stepHeight(vehicle));
    }

    private static double clearanceAlong(Entity vehicle, float heading) {
        double radians = Math.toRadians(heading);
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(
                -Math.sin(radians), 0.0D, Math.cos(radians));
        return VehicleTerrain.clearance(vehicle.level(), vehicle.getBoundingBox(), forward, PROBE_RANGE,
                stepHeight(vehicle));
    }

    private static java.util.List<net.minecraft.world.phys.Vec3> corners(Entity vehicle) {
        net.minecraft.world.phys.AABB box = vehicle.getBoundingBox();
        double y = box.minY + (box.maxY - box.minY) * 0.5D;
        return java.util.List.of(
                new net.minecraft.world.phys.Vec3(box.minX, y, box.minZ),
                new net.minecraft.world.phys.Vec3(box.maxX, y, box.minZ),
                new net.minecraft.world.phys.Vec3(box.minX, y, box.maxZ),
                new net.minecraft.world.phys.Vec3(box.maxX, y, box.maxZ));
    }

    private static double rayLength(Entity vehicle, net.minecraft.world.phys.Vec3 from,
            net.minecraft.world.phys.Vec3 direction, double range) {
        net.minecraft.world.phys.Vec3 to = from.add(direction.scale(range));
        net.minecraft.world.phys.BlockHitResult hit = vehicle.level().clip(new net.minecraft.world.level.ClipContext(
                from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, vehicle));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return range;
        }
        return from.distanceTo(hit.getLocation());
    }

    private static void ensureEngine(Entity vehicle) {
        try {
            if (isEngineOn != null && toggleEngine != null && !(Boolean) isEngineOn.invoke(vehicle)) {
                toggleEngine.invoke(vehicle, Boolean.TRUE);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
        }
    }

    private static boolean lineClear(Entity vehicle, LivingEntity target) {
        net.minecraft.world.phys.Vec3 from = vehicle.position().add(0.0D, vehicle.getBbHeight() * 0.7D, 0.0D);
        net.minecraft.world.phys.Vec3 to = target.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = vehicle.level().clip(new net.minecraft.world.level.ClipContext(
                from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, vehicle));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static net.minecraft.world.phys.Vec3 flankAim(NeutralHumanoid humanoid, Entity vehicle, LivingEntity target) {
        int cooldown = FLANK_COOLDOWN.getOrDefault(humanoid.getId(), 0);
        if (cooldown > 0) {
            FLANK_COOLDOWN.put(humanoid.getId(), cooldown - 10);
            return FLANK_AIM.get(humanoid.getId());
        }
        int attempts = FLANK_ATTEMPTS.getOrDefault(humanoid.getId(), 0);
        if (attempts >= 2) {
            FLANK_ATTEMPTS.remove(humanoid.getId());
            FLANK_AIM.remove(humanoid.getId());
            return null;
        }
        net.minecraft.world.phys.Vec3 spot = findFlankSpot(vehicle, target);
        if (spot == null) {
            FLANK_ATTEMPTS.put(humanoid.getId(), attempts + 1);
            FLANK_COOLDOWN.put(humanoid.getId(), 40);
            return null;
        }
        FLANK_ATTEMPTS.put(humanoid.getId(), attempts + 1);
        FLANK_COOLDOWN.put(humanoid.getId(), 80);
        FLANK_AIM.put(humanoid.getId(), spot);
        return spot;
    }

    private static net.minecraft.world.phys.Vec3 findFlankSpot(Entity vehicle, LivingEntity target) {
        net.minecraft.core.BlockPos targetPos = target.blockPosition();
        for (int i = 0; i < 12; i++) {
            double angle = (i / 12.0D) * Math.PI * 2.0D;
            double radius = 12.0D + (i % 3) * 5.0D;
            net.minecraft.core.BlockPos spot = net.minecraft.core.BlockPos.containing(
                    targetPos.getX() + Math.cos(angle) * radius, target.getY(),
                    targetPos.getZ() + Math.sin(angle) * radius);
            if (!com.curiats.returncapsule.entity.humanoid.HumanoidTraversal.canStandOn(vehicle.level(), spot)) {
                continue;
            }
            net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atCenterOf(spot)
                    .add(0.0D, 1.5D, 0.0D);
            net.minecraft.world.phys.BlockHitResult hit = vehicle.level().clip(new net.minecraft.world.level.ClipContext(
                    from, target.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, vehicle));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return net.minecraft.world.phys.Vec3.atCenterOf(spot);
            }
        }
        return null;
    }

    private static double clearanceTo(Entity vehicle, LivingEntity target, net.minecraft.world.entity.player.Player owner) {
        Entity other = target != null ? target : owner;
        if (other == null) {
            return 0.0D;
        }
        double mine = (vehicle.getBbWidth() + vehicle.getBbHeight()) * 0.5D;
        double theirs = (other.getBbWidth() + other.getBbHeight()) * 0.5D;
        return mine + theirs + 4.0D;
    }

    private static double weaponRange(Entity vehicle) {
        try {
            java.lang.reflect.Field seatsField = vehicle.getClass().getField("seats");
            Object seats = seatsField.get(vehicle);
            if (!(seats instanceof java.util.List<?> list)) {
                return 0.0D;
            }
            double best = 0.0D;
            for (Object seat : list) {
                Object unit = seat.getClass().getField("partUnit").get(seat);
                if (unit == null) {
                    continue;
                }
                java.lang.reflect.Field rangeField;
                try {
                    rangeField = unit.getClass().getField("weaponHitRange");
                } catch (NoSuchFieldException missing) {
                    continue;
                }
                best = Math.max(best, rangeField.getDouble(unit));
            }
            return best;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return 0.0D;
        }
    }

    public static String describeDriver(NeutralHumanoid humanoid) {
        Entity vehicle = humanoid.getVehicle();
        if (vehicle == null || !resolve()) {
            return "drive=none";
        }
        try {
            List<?> seats = (List<?>) seatsField.get(vehicle);
            Object seatZero = seats.get(0);
            Object riderId = seatZero.getClass().getField("passengerId").get(seatZero);
            boolean drives = riderId instanceof Integer id && id == humanoid.getId();
            net.minecraft.world.entity.player.Player owner = humanoid.level().getNearestPlayer(humanoid, 256.0D);
            boolean ownerAboard = owner != null && owner.getVehicle() == vehicle;
            String mode = humanoid.getTarget() != null ? "target"
                    : owner != null && !ownerAboard ? "player" : "idle";
            double distance = owner == null ? -1.0D : Math.sqrt(humanoid.distanceToSqr(owner));
            int detour = DETOUR.getOrDefault(humanoid.getId(), 0);
            return "drive=" + drives + " mode=" + mode + " ownerDist=" + Math.round(distance)
                    + " detour=" + detour + " detourTicks=" + DETOUR_TICKS.getOrDefault(humanoid.getId(), 0)
                    + " jam=" + JAM.getOrDefault(humanoid.getId(), 0)
                    + " aheadClear=" + Math.round(clearanceAlong(vehicle, vehicle.getYRot()));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "drive=error";
        }
    }

    private static void stopAll(Object control) throws IllegalAccessException {
        forwardField.setBoolean(control, false);
        backwardField.setBoolean(control, false);
        leftField.setBoolean(control, false);
        rightField.setBoolean(control, false);
        handbrakeField.setBoolean(control, true);
        leftYawField.setBoolean(control, false);
        rightYawField.setBoolean(control, false);
        yRotKeepField.setBoolean(control, false);
    }

    private static double yawErrorTo(Entity vehicle, net.minecraft.world.phys.Vec3 aim) {
        double dx = aim.x - vehicle.getX();
        double dz = aim.z - vehicle.getZ();
        float desired = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float delta = net.minecraft.util.Mth.wrapDegrees(desired - vehicle.getYRot());
        return delta;
    }

    private static boolean resolve() {
        if (resolved) {
            return !broken;
        }
        resolved = true;
        try {
            Class<?> vehicleClass = Class.forName(VEHICLE_CLASS);
            controlUnitField = vehicleClass.getField("controlUnit");
            seatsField = vehicleClass.getField("seats");
            toggleEngine = vehicleClass.getMethod("toggleEngine", Boolean.class);
            isEngineOn = vehicleClass.getMethod("isEngineOn");
            Class<?> controlClass = Class.forName(CONTROL_CLASS);
            forwardField = controlClass.getField("forward");
            backwardField = controlClass.getField("backward");
            leftField = controlClass.getField("left");
            rightField = controlClass.getField("right");
            handbrakeField = controlClass.getField("up");
            leftYawField = controlClass.getField("leftYaw");
            rightYawField = controlClass.getField("rightYaw");
            yRotField = controlClass.getField("yRot");
            yRotKeepField = controlClass.getField("yRotKeep");
            downField = controlClass.getField("down");
            xRotField = controlClass.getField("xRot");
            xRotKeepField = controlClass.getField("xRotKeep");
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            return false;
        }
    }
}
