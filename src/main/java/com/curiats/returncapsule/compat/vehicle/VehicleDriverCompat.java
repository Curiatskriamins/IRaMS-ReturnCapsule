package com.curiats.returncapsule.compat.vehicle;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.HumanoidSight;
import com.curiats.returncapsule.entity.humanoid.HumanoidTraversal;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class VehicleDriverCompat {

    private static final String VEHICLE_CLASS = "org.ywzj.vehicle.entity.vehicle.AbstractVehicle";
    private static final String CONTROL_CLASS = "org.ywzj.vehicle.vehicle.control.ControlUnit";

    private static final double PROBE_RANGE = 10.0D;
    private static final double MIN_ENGAGE_RANGE = 48.0D;
    private static final double KITE_RANGE = 22.0D;
    /** Four blocks of clear air in front of the bumper, as requested. */
    private static final double FRONT_GAP = 4.0D;
    private static final double FOLLOW_START_MARGIN = 4.0D;
    private static final double FOLLOW_TRAIL_TICKS = 4.0D;
    private static final double HOLD_DEADBAND = 3.0D;
    private static final double ORDER_KEEP_RANGE = 4.0D;
    private static final double ENEMY_RANGE = 64.0D;
    private static final double ESCORT_RADIUS = 20.0D;
    private static final double ESCORT_ALTITUDE = 14.0D;
    private static final double ORBIT_STEP = 0.02D;
    private static final double CRUISE_PITCH = 12.0D;
    private static final double LAND_SEARCH_RADIUS = 64.0D;
    private static final double LAND_ALTITUDE = 12.0D;
    private static final double MAX_SINK = 0.25D;
    private static final int STEER_HOLD = 6;
    private static final double STEER_HYSTERESIS = 12.0D;
    private static final int SAMPLE_TICKS = 20;
    private static final double SAMPLE_MIN_MOVE = 1.4D;
    private static final int RECOVER_TICKS = 40;
    private static final int ENTITY_WAIT_LIMIT = 60;
    private static final int BLOCKED_LIMIT = 24;
    private static final double LANE_PROBE = 8.0D;
    private static final double LANE_SHIFT = 2.0D;
    private static final double CORRIDOR_PROBE = 5.0D;
    private static final double CORRIDOR_REACH = 14.0D;
    private static final double CORRIDOR_STEP = 4.0D;
    private static final double CORRIDOR_SHIFT = 3.0D;
    private static final double DETOUR_ANGLE = 60.0D;
    private static final int DETOUR_TICKS = 60;
    private static final int TURN_STALL_LIMIT = 60;
    private static final int TURN_BOOST_TICKS = 30;
    private static final int REVERSE_LIMIT = 60;
    private static final int REVERSE_BREAK_TICKS = 30;
    private static final int FLANK_TICKS = 60;
    private static final int FLANK_COOLDOWN = 100;
    private static final int PATROL_TURN_TICKS = 30;
    private static final int PATROL_INTERVAL_MIN = 80;
    private static final int PATROL_INTERVAL_MAX = 180;
    private static final float PATROL_TURN_ANGLE = 70.0F;
    private static final int LOG_LIMIT = 400;

    private enum Mode {
        IDLE, FOLLOW, COMBAT, ORDER, FLANK, PATROL
    }

    private static final class State {
        private boolean forward;
        private boolean backward;
        private boolean following;
        private int steerSide;
        private int steerHold;
        private int entityWait;
        private int blockedTicks;
        private int pivotTicks;
        private int turnBoostTicks;
        private int detourTicks;
        private int detourSide;
        private int reverseTicks;
        private int reverseBreakTicks;
        private int flankTicks;
        private int flankCooldown;
        private int flankTargetId = -1;
        private Vec3 flankAim;
        private int patrolTimer;
        private int patrolTicks;
        private float patrolYaw;
        private int sampleAge;
        private Vec3 sample;
        private int recoverTicks;
        private int recoverSide;
        private int recoverCount;
        private double weaponRange;
        private int weaponRangeAge;
    }

    private static final class Flight {
        private double orbit;
        private BlockPos pad;
        private LivingEntity enemy;
        private int enemyTicks;
    }

    private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();
    private static final Map<Integer, UUID> OWNERS = new ConcurrentHashMap<>();
    private static final Map<Integer, Flight> FLIGHTS = new ConcurrentHashMap<>();
    private static final AtomicLong LOGS = new AtomicLong();

    private static volatile boolean straightFollow;

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
    private static Field seatPassengerIdField;
    private static Method resetControl;
    private static Method getOperator;
    private static Method getDriver;
    private static Method toggleEngine;
    private static Method isEngineOn;

    private VehicleDriverCompat() {
    }

    public static void setStraightFollow(boolean enabled) {
        straightFollow = enabled;
    }

    public static boolean straightFollow() {
        return straightFollow;
    }

    public static void tick(NeutralHumanoid humanoid) {
        if (humanoid.level().isClientSide || humanoid.isDowned()
                || !humanoid.isAlive() || humanoid.isRemoved()) {
            release(humanoid);
            return;
        }
        Entity vehicle = humanoid.getVehicle();
        if (vehicle == null) {
            humanoid.setDrivingVehicle(false);
            forget(humanoid.getId());
            return;
        }
        if (!resolve()) {
            humanoid.setDrivingVehicle(false);
            return;
        }
        if (!controlUnitField.getDeclaringClass().isInstance(vehicle)) {
            humanoid.setDrivingVehicle(false);
            forget(humanoid.getId());
            return;
        }
        Object control;
        try {
            control = controlUnitField.get(vehicle);
        } catch (IllegalAccessException exception) {
            return;
        }
        if (control == null) {
            return;
        }
        boolean driving = isDriver(vehicle, humanoid);
        humanoid.setDrivingVehicle(driving);
        if (!driving) {
            forget(humanoid.getId());
            return;
        }
        if (isPlayerOperated(control)) {
            return;
        }
        try {
            if (resetControl != null) {
                resetControl.invoke(control);
            }
            if (isFixedWing(vehicle)) {
                FLIGHTS.remove(vehicle.getId());
                parkFlight(control);
                return;
            }
            if (isRotaryWing(vehicle)) {
                FLIGHTS.computeIfAbsent(vehicle.getId(), key -> new Flight());
                fly(vehicle, control);
                return;
            }
            FLIGHTS.remove(vehicle.getId());
            drive(humanoid, vehicle, control);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("[drive] control failure", exception);
        }
    }

    private static void drive(NeutralHumanoid humanoid, Entity vehicle, Object control)
            throws IllegalAccessException {
        int driverId = humanoid.getId();
        State state = STATES.computeIfAbsent(driverId, key -> new State());
        ensureEngine(vehicle);
        HullTerrain.refresh(vehicle);

        tickTimers(state);

        LivingEntity target = humanoid.getTarget();
        if (target != null && !VehicleGunner.armed(vehicle)) {
            target = null;
        }
        Player owner = findOwner(humanoid);
        if (owner != null && owner.level() != vehicle.level()) {
            owner = null;
        }
        boolean ownerAboard = owner != null && owner.getVehicle() == vehicle;
        Vec3 order = VehicleOrders.take(vehicle);

        Mode mode = Mode.IDLE;
        Vec3 goal = null;
        double keep = 0.0D;
        double distance = Double.MAX_VALUE;

        if (straightFollow && owner != null && !ownerAboard) {
            mode = Mode.FOLLOW;
            goal = owner.position();
            keep = followKeep(vehicle);
            distance = vehicle.distanceTo(owner);
        } else if (order != null) {
            mode = Mode.ORDER;
            goal = order;
            keep = ORDER_KEEP_RANGE;
            distance = horizontalDistance(vehicle, order);
        } else if (target != null) {
            if (state.weaponRangeAge-- <= 0) {
                state.weaponRange = weaponRange(vehicle);
                state.weaponRangeAge = 40;
            }
            if (state.flankTargetId != target.getId()) {
                state.flankTicks = 0;
                state.flankAim = null;
            }
            if (lineClear(vehicle, target)) {
                state.flankTicks = 0;
                state.flankAim = null;
            } else if (state.flankTicks > 0 && state.flankAim != null) {
                mode = Mode.FLANK;
                goal = state.flankAim;
                keep = ORDER_KEEP_RANGE;
                distance = horizontalDistance(vehicle, goal);
            } else if (state.flankCooldown <= 0) {
                Vec3 spot = findFlankSpot(vehicle, target);
                if (spot != null) {
                    state.flankTargetId = target.getId();
                    state.flankAim = spot;
                    state.flankTicks = FLANK_TICKS;
                    state.flankCooldown = FLANK_COOLDOWN;
                    mode = Mode.FLANK;
                    goal = spot;
                    keep = ORDER_KEEP_RANGE;
                    distance = horizontalDistance(vehicle, spot);
                } else {
                    state.flankCooldown = 40;
                }
            }
            if (mode == Mode.IDLE) {
                mode = Mode.COMBAT;
                goal = target.position();
                keep = Math.max(MIN_ENGAGE_RANGE, state.weaponRange);
                distance = vehicle.distanceTo(target);
            }
        } else if (owner != null && !ownerAboard
                && (state.following
                    ? vehicle.distanceTo(owner) > followKeep(vehicle)
                    : humanoid.distanceToSqr(owner) > followStart(vehicle) * followStart(vehicle))) {
            mode = Mode.FOLLOW;
            goal = followPoint(vehicle, owner);
            keep = followKeep(vehicle);
            distance = vehicle.distanceTo(owner);
        } else if (owner == null) {
            if (state.patrolTicks > 0) {
                state.patrolTicks--;
                mode = Mode.PATROL;
                goal = patrolPoint(vehicle, state.patrolYaw);
            } else if (state.patrolTimer-- <= 0) {
                state.patrolYaw = (vehicle.level().random.nextFloat() * 2.0F - 1.0F) * PATROL_TURN_ANGLE;
                state.patrolTicks = PATROL_TURN_TICKS;
                state.patrolTimer = PATROL_INTERVAL_MIN
                        + vehicle.level().random.nextInt(PATROL_INTERVAL_MAX - PATROL_INTERVAL_MIN + 1);
                mode = Mode.PATROL;
                goal = patrolPoint(vehicle, state.patrolYaw);
            }
        }

        if ((mode == Mode.COMBAT || mode == Mode.FLANK) && owner != null && !ownerAboard
                && humanoid.distanceToSqr(owner) > (followStart(vehicle) * 2.0D) * (followStart(vehicle) * 2.0D)
                && vehicle.distanceTo(target) > 24.0D) {
            mode = Mode.FOLLOW;
            goal = followPoint(vehicle, owner);
            keep = followKeep(vehicle);
            distance = vehicle.distanceTo(owner);
            state.flankTicks = 0;
            state.flankAim = null;
        }

        if (mode == Mode.ORDER && distance <= keep) {
            VehicleOrders.clear(vehicle);
            mode = Mode.IDLE;
        }
        if (mode == Mode.FLANK && distance <= keep) {
            state.flankTicks = 0;
            state.flankAim = null;
            state.flankCooldown = Math.max(state.flankCooldown, 40);
            mode = Mode.IDLE;
        }
        if (mode == Mode.PATROL && goal != null) {
            distance = horizontalDistance(vehicle, goal);
        }
        if (target != null || owner != null) {
            state.patrolTicks = 0;
            state.patrolTimer = 0;
        } else if (mode != Mode.PATROL) {
            state.patrolTicks = 0;
        }

        state.following = mode == Mode.FOLLOW;

        if (mode == Mode.IDLE || goal == null) {
            settle(state);
            VehiclePathPlanner.forget(vehicle.getId());
            stopAll(control);
            return;
        }

        boolean targetAhead = true;
        if (target != null) {
            double yawRadians = Math.toRadians(vehicle.getYRot());
            double forwardX = -Math.sin(yawRadians);
            double forwardZ = Math.cos(yawRadians);
            Vec3 toTarget = target.position().subtract(vehicle.position());
            targetAhead = toTarget.x * forwardX + toTarget.z * forwardZ > 0.0D;
        }

        boolean patrol = mode == Mode.PATROL;
        double band = state.forward ? 0.0D : HOLD_DEADBAND;
        boolean forward;
        boolean backward = false;
        if (patrol) {
            forward = true;
        } else if (mode == Mode.COMBAT) {
            forward = distance > keep + band;
            backward = distance < KITE_RANGE && targetAhead && rearUsable(vehicle);
            if (!targetAhead && !backward && distance <= keep + HOLD_DEADBAND) {
                forward = true;
            }
        } else if (mode == Mode.ORDER || mode == Mode.FLANK) {
            forward = distance > keep;
        } else {
            forward = distance > (state.forward ? keep : followStart(vehicle));
        }

        Vec3 aim = goal;
        if (!patrol && !straightFollow && forward && distance > 6.0D) {
            Vec3 routed = VehiclePathPlanner.route(vehicle, goal);
            if (routed != null) {
                aim = routed;
            }
        }
        if (!patrol && !straightFollow && state.detourTicks <= 0 && state.recoverTicks <= 0) {
            aim = laneBias(vehicle, aim);
        }
        if (aim.distanceToSqr(vehicle.position()) < 9.0D) {
            aim = goal;
        }

        double yawError = yawErrorTo(vehicle, aim);
        double absYaw = Math.abs(yawError);
        double speed = horizontalSpeed(vehicle);
        // Measure along the direction of travel, not along the nose: while the hull is still turning, its nose points
        // at whatever stands beside the route, and reading that as "blocked" is what let a single post freeze a hull.
        float aimHeading = headingTo(vehicle, aim.x, aim.z);
        float checkHeading = absYaw > 30.0D ? aimHeading : vehicle.getYRot();
        double front = clearanceAhead(vehicle, checkHeading, PROBE_RANGE);
        double brakeDistance = Math.min(9.0D, speed * speed / 0.04D + vehicle.getBbWidth() * 0.5D + 1.0D);
        boolean blocked = front < Math.max(2.0D, brakeDistance);
        boolean ground = groundAhead(vehicle, checkHeading);
        boolean entity = entityAhead(vehicle, owner, target);

        boolean wantedForward = forward;
        boolean forcedTurn = false;
        if (forward && blocked) {
            Double offset = sidestepOffset(vehicle, checkHeading);
            if (offset != null) {
                // A post or a single block is not worth a three point turn: move the aim past it and keep rolling.
                double radians = Math.toRadians(checkHeading);
                Vec3 right = new Vec3(-Math.cos(radians), 0.0D, -Math.sin(radians));
                aim = aim.add(right.scale(offset));
                forcedTurn = true;
            } else if (!isTracked(vehicle) && absYaw > 35.0D && ground
                    && front > vehicle.getBbWidth() * 0.5D + 0.5D) {
                forcedTurn = true;
            } else {
                forward = false;
            }
        }
        if (forward && !ground) {
            forward = false;
        }
        if (wantedForward && blocked && !forcedTurn && speed < 0.1D
                && state.recoverTicks <= 0) {
            state.blockedTicks++;
        } else if (!wantedForward || speed >= 0.1D || state.recoverTicks > 0) {
            state.blockedTicks = 0;
        }

        if (!straightFollow && state.detourTicks <= 0 && wantedForward && blocked
                && !forcedTurn && ground && !entity && speed < 0.15D
                && state.blockedTicks < BLOCKED_LIMIT && state.recoverTicks <= 0) {
            startDetour(state, vehicle);
        }
        boolean detour = state.detourTicks > 0;
        if (detour) {
            state.detourTicks--;
            if (!entity) {
                boolean frontRoom = clearanceAhead(vehicle, checkHeading, 3.0D) > 2.0D;
                boolean rearRoom = rearUsable(vehicle);
                if (isTracked(vehicle)) {
                    // Tracks pivot; backing out of a turn is wheeled behaviour and reads as broken driving.
                    forward = frontRoom && ground && absYaw < 55.0D;
                    backward = false;
                } else {
                    boolean reversePhase = (state.detourTicks / 15) % 2 == 0;
                    forward = !reversePhase && frontRoom && ground;
                    backward = reversePhase && rearRoom;
                }
            } else {
                forward = false;
                backward = false;
            }
        }

        boolean pivot = false;
        if (!detour && state.recoverTicks <= 0 && isTracked(vehicle) && wantedForward
                && (absYaw > 25.0D || blocked) && speed < 0.3D && !entity) {
            forward = false;
            pivot = true;
            state.blockedTicks = 0;
            state.pivotTicks++;
            if (state.pivotTicks > TURN_STALL_LIMIT) {
                state.turnBoostTicks = TURN_BOOST_TICKS;
                state.pivotTicks = 0;
            }
        } else if (!detour) {
            state.pivotTicks = 0;
        }
        if (state.turnBoostTicks > 0 && !detour) {
            state.turnBoostTicks--;
            if (!blocked && ground && !entity) {
                forward = true;
                backward = false;
                pivot = false;
            } else {
                state.turnBoostTicks = 0;
                forward = false;
                startRecovery(state, vehicle, yawError);
            }
        }

        if (!detour && state.recoverTicks <= 0 && state.turnBoostTicks <= 0) {
            if (backward || state.backward) {
                state.reverseTicks++;
            } else {
                state.reverseTicks = 0;
            }
            if (state.reverseTicks > REVERSE_LIMIT && !rearUsable(vehicle)
                    && !entity && ground && front > 3.0D) {
                state.reverseBreakTicks = REVERSE_BREAK_TICKS;
                state.reverseTicks = 0;
            }
            if (state.reverseBreakTicks > 0) {
                if (!blocked && ground && !entity) {
                    state.reverseBreakTicks--;
                    forward = true;
                    backward = false;
                } else {
                    state.reverseBreakTicks = 0;
                }
            }
        } else {
            state.reverseTicks = 0;
        }

        if (entity) {
            forward = false;
            if (wantedForward || state.forward) {
                state.entityWait++;
                if (state.entityWait > ENTITY_WAIT_LIMIT && !backward && rearUsable(vehicle)) {
                    backward = true;
                }
            } else {
                state.entityWait = 0;
            }
        } else {
            state.entityWait = 0;
        }

        if (forward) {
            if (state.sample == null) {
                state.sample = vehicle.position();
                state.sampleAge = 0;
            }
            state.sampleAge++;
            if (state.sampleAge >= SAMPLE_TICKS) {
                if (state.sample.distanceToSqr(vehicle.position()) < SAMPLE_MIN_MOVE * SAMPLE_MIN_MOVE) {
                    startRecovery(state, vehicle, yawError);
                }
                state.sample = vehicle.position();
                state.sampleAge = 0;
            }
        } else {
            state.sample = null;
            state.sampleAge = 0;
        }

        if (state.blockedTicks > BLOCKED_LIMIT) {
            startRecovery(state, vehicle, yawError);
        }

        boolean recovering = state.recoverTicks > 0;
        if (recovering) {
            state.recoverTicks--;
            forward = false;
            backward = !isTracked(vehicle) && rearUsable(vehicle);
            pivot = isTracked(vehicle);
            state.detourTicks = 0;
            state.turnBoostTicks = 0;
            state.reverseTicks = 0;
            state.reverseBreakTicks = 0;
            if (state.recoverTicks == 0) {
                state.blockedTicks = 0;
                state.sample = null;
                state.sampleAge = 0;
            }
        }

        int desired = 0;
        if (backward != state.backward) {
            state.steerSide = 0;
            state.steerHold = 0;
        }
        if (recovering) {
            desired = state.recoverSide;
        } else if (detour) {
            desired = state.detourSide;
        } else if ((forward || backward || pivot) && absYaw > 4.0D) {
            desired = yawError < 0.0D ? -1 : 1;
            if (state.steerHold > 0 && desired != state.steerSide && absYaw < STEER_HYSTERESIS) {
                desired = state.steerSide;
            }
            if (desired != state.steerSide) {
                state.steerSide = desired;
                state.steerHold = STEER_HOLD;
            } else if (state.steerHold > 0) {
                state.steerHold--;
            }
        } else {
            state.steerSide = 0;
            state.steerHold = 0;
        }

        int wheel = backward ? -desired : desired;
        boolean left = wheel < 0;
        boolean right = wheel > 0;
        boolean handbrake = !forward && !backward && !pivot;
        state.forward = forward;
        state.backward = backward;

        write(control, forward, backward, left, right, handbrake);

        if (humanoid.tickCount % 20 == 0 && LOGS.incrementAndGet() <= LOG_LIMIT) {
            ReturnCapsuleMod.LOGGER.info(
                    "[drive] {} mode={} target={} owner={} yaw={} dist={} front={} fwd={} back={} steer={} recover={} detour={} flank={} turn={} patrol={} blocked={} brake={} straight={}",
                    vehicle.getType().toString(), mode, target != null, owner != null,
                    format(yawError), format(distance), format(front), forward, backward, wheel,
                    state.recoverTicks, state.detourTicks, state.flankTicks, state.turnBoostTicks,
                    state.patrolTicks, blocked, handbrake, straightFollow);
        }
    }

    private static void settle(State state) {
        state.forward = false;
        state.backward = false;
        state.following = false;
        state.blockedTicks = 0;
        state.pivotTicks = 0;
        state.turnBoostTicks = 0;
        state.detourTicks = 0;
        state.detourSide = 0;
        state.reverseTicks = 0;
        state.reverseBreakTicks = 0;
        state.flankTicks = 0;
        state.flankAim = null;
        state.flankTargetId = -1;
        state.entityWait = 0;
        state.sample = null;
        state.sampleAge = 0;
        state.recoverTicks = 0;
        state.steerSide = 0;
        state.steerHold = 0;
    }

    private static void startRecovery(State state, Entity vehicle, double yawError) {
        if (straightFollow || state.recoverTicks > 0) {
            return;
        }
        state.recoverTicks = RECOVER_TICKS;
        int toward = yawError < 0.0D ? -1 : 1;
        state.recoverSide = (state.recoverCount++ & 1) == 0 ? toward : -toward;
        state.blockedTicks = 0;
        state.pivotTicks = 0;
        state.turnBoostTicks = 0;
        state.detourTicks = 0;
        state.detourSide = 0;
        state.reverseTicks = 0;
        state.reverseBreakTicks = 0;
        state.steerSide = 0;
        state.steerHold = 0;
        state.sample = null;
        state.sampleAge = 0;
        VehiclePathPlanner.forget(vehicle.getId());
    }

    private static void startDetour(State state, Entity vehicle) {
        double left = clearanceAhead(vehicle, vehicle.getYRot() - (float) DETOUR_ANGLE, 8.0D);
        double right = clearanceAhead(vehicle, vehicle.getYRot() + (float) DETOUR_ANGLE, 8.0D);
        state.detourSide = left >= right ? -1 : 1;
        state.detourTicks = DETOUR_TICKS;
        state.pivotTicks = 0;
        state.turnBoostTicks = 0;
        state.reverseTicks = 0;
        state.reverseBreakTicks = 0;
        state.steerSide = 0;
        state.steerHold = 0;
        VehiclePathPlanner.forget(vehicle.getId());
    }

    private static void tickTimers(State state) {
        if (state.flankTicks > 0) {
            state.flankTicks--;
            if (state.flankTicks == 0) {
                state.flankAim = null;
            }
        }
        if (state.flankCooldown > 0) {
            state.flankCooldown--;
        }
    }

    /**
     * Signed sideways offset that gets the hull past whatever is in front, or null when neither side has room.
     *
     * <p>Measured with the cached hull slice at a point just beyond the obstacle, so it answers "can I slip past this
     * post" rather than "is the post there", which is the difference between rolling around it and pivoting at it.</p>
     */
    private static Double sidestepOffset(Entity vehicle, float heading) {
        double radians = Math.toRadians(heading);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        Vec3 right = new Vec3(-Math.cos(radians), 0.0D, -Math.sin(radians));
        AABB probe = vehicle.getBoundingBox().move(forward.x * 2.5D, 0.0D, forward.z * 2.5D);
        double half = vehicle.getBbWidth() * 0.5D;
        double reach = half + 3.0D;
        double left = HullTerrain.clearance(vehicle, probe, right.scale(-1.0D), reach, stepHeight(vehicle));
        double rightRoom = HullTerrain.clearance(vehicle, probe, right, reach, stepHeight(vehicle));
        boolean leftOk = left >= half + 1.0D;
        boolean rightOk = rightRoom >= half + 1.0D;
        if (!leftOk && !rightOk) {
            return null;
        }
        double needed = half + 1.0D;
        if (leftOk && (!rightOk || left >= rightRoom)) {
            return -Math.min(3.0D, Math.max(1.0D, needed - left + 1.0D));
        }
        return Math.min(3.0D, Math.max(1.0D, needed - rightRoom + 1.0D));
    }

    private static Vec3 laneBias(Entity vehicle, Vec3 aim) {
        Vec3 direction = new Vec3(aim.x - vehicle.getX(), 0.0D, aim.z - vehicle.getZ());
        double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (length < 6.0D || Math.abs(yawErrorTo(vehicle, aim)) > 50.0D) {
            return aim;
        }
        direction = new Vec3(direction.x / length, 0.0D, direction.z / length);
        Vec3 right = new Vec3(-direction.z, 0.0D, direction.x);
        AABB box = vehicle.getBoundingBox();
        double step = stepHeight(vehicle);
        // Same cached slice the driving checks use: this used to re-scan the block grid twice per lane probe.
        double rightClear = HullTerrain.clearance(vehicle, box, right, LANE_PROBE, step);
        double leftClear = HullTerrain.clearance(vehicle, box, right.scale(-1.0D), LANE_PROBE, step);
        double shift = Math.max(-LANE_SHIFT, Math.min(LANE_SHIFT, (rightClear - leftClear) * 0.5D));
        if (rightClear <= LANE_PROBE && leftClear <= LANE_PROBE) {
            return shift == 0.0D ? aim : aim.add(right.scale(shift));
        }
        double reach = Math.min(CORRIDOR_REACH, length);
        for (double ahead = CORRIDOR_STEP; ahead <= reach; ahead += CORRIDOR_STEP) {
            AABB moved = box.move(direction.x * ahead, 0.0D, direction.z * ahead);
            double aheadRight = HullTerrain.clearance(vehicle, moved, right, CORRIDOR_PROBE, step);
            double aheadLeft = HullTerrain.clearance(vehicle, moved, right.scale(-1.0D),
                    CORRIDOR_PROBE, step);
            if (aheadRight > CORRIDOR_PROBE || aheadLeft > CORRIDOR_PROBE) {
                continue;
            }
            double corridorShift = Math.max(-CORRIDOR_SHIFT,
                    Math.min(CORRIDOR_SHIFT, (aheadRight - aheadLeft) * 0.5D));
            return Math.abs(corridorShift) < 0.2D ? aim : aim.add(right.scale(corridorShift));
        }
        return Math.abs(shift) < 0.2D ? aim : aim.add(right.scale(shift));
    }

    private static Vec3 patrolPoint(Entity vehicle, float offset) {
        double radians = Math.toRadians(vehicle.getYRot() + offset);
        return vehicle.position().add(-Math.sin(radians) * 10.0D, 0.0D, Math.cos(radians) * 10.0D);
    }

    private static boolean lineClear(Entity vehicle, LivingEntity target) {
        Vec3 from = vehicle.position().add(0.0D, vehicle.getBbHeight() * 0.7D, 0.0D);
        Vec3 to = target.getEyePosition();
        BlockHitResult hit = vehicle.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vehicle));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static Vec3 findFlankSpot(Entity vehicle, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();
        for (int i = 0; i < 12; i++) {
            double angle = (i / 12.0D) * Math.PI * 2.0D;
            double radius = 28.0D + (i % 3) * 5.0D;
            BlockPos spot = BlockPos.containing(
                    targetPos.getX() + Math.cos(angle) * radius, targetPos.getY(),
                    targetPos.getZ() + Math.sin(angle) * radius);
            if (!HumanoidTraversal.canStandOn(vehicle.level(), spot)) {
                continue;
            }
            Vec3 from = Vec3.atCenterOf(spot).add(0.0D, 1.5D, 0.0D);
            BlockHitResult hit = vehicle.level().clip(new ClipContext(from, target.getEyePosition(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vehicle));
            if (hit.getType() == HitResult.Type.MISS) {
                return Vec3.atCenterOf(spot);
            }
        }
        return null;
    }

    private static void release(NeutralHumanoid humanoid) {
        HullTerrain.forget(humanoid.getVehicle() == null ? -1 : humanoid.getVehicle().getId());
        boolean wasDriving = humanoid.isDrivingVehicle();
        forget(humanoid.getId());
        Entity vehicle = humanoid.getVehicle();
        if (vehicle == null) {
            return;
        }
        if (!resolve()) {
            return;
        }
        if (!controlUnitField.getDeclaringClass().isInstance(vehicle)) {
            return;
        }
        try {
            Object control = controlUnitField.get(vehicle);
            if (control == null || (!isDriver(vehicle, humanoid) && !wasDriving)) {
                return;
            }
            FLIGHTS.remove(vehicle.getId());
            VehiclePathPlanner.forget(vehicle.getId());
            VehicleOrders.clear(vehicle);
            if (isPlayerOperated(control)) {
                return;
            }
            if (resetControl != null) {
                resetControl.invoke(control);
            }
            if (isFixedWing(vehicle)) {
                parkFlight(control);
            } else if (isRotaryWing(vehicle)) {
                centreSticks(control);
            } else {
                stopAll(control);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void forget(int humanoidId) {
        STATES.remove(humanoidId);
        OWNERS.remove(humanoidId);
    }

    private static Player findOwner(NeutralHumanoid humanoid) {
        Player near = humanoid.level().getNearestPlayer(humanoid, 256.0D);
        if (near != null) {
            OWNERS.put(humanoid.getId(), near.getUUID());
            return near;
        }
        UUID remembered = OWNERS.get(humanoid.getId());
        if (remembered == null) {
            return null;
        }
        if (humanoid.level() instanceof ServerLevel level && level.getServer() != null) {
            return level.getServer().getPlayerList().getPlayer(remembered);
        }
        return null;
    }

    /** Half of the hull footprint, used as the distance from the entity centre to its bumper. */
    private static double frontOffset(Entity vehicle) {
        return Math.max(1.0D, vehicle.getBbWidth() * 0.5D);
    }

    /** Keep distance measured at the centre: four blocks of clear air in front of the bumper. */
    private static double followKeep(Entity vehicle) {
        return FRONT_GAP + frontOffset(vehicle);
    }

    /** Hysteresis: only start reloading the approach once the gap grows past this. */
    private static double followStart(Entity vehicle) {
        return followKeep(vehicle) + FOLLOW_START_MARGIN;
    }

    private static Vec3 followPoint(Entity vehicle, Player owner) {
        Vec3 motion = owner.getDeltaMovement();
        Vec3 trail = owner.position().subtract(motion.scale(FOLLOW_TRAIL_TICKS));
        // One slot per hull so a convoy spreads out behind the owner instead of all aiming at the same point.
        int slot = Math.floorMod(vehicle.getId(), 5);
        double radians = Math.toRadians(owner.getYRot());
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double lateral = (slot - 2) * 3.0D;
        double behind = 3.0D + (slot % 2) * 4.0D;
        return trail.subtract(forward.scale(behind)).add(right.scale(lateral));
    }

    private static boolean isDriver(Entity vehicle, NeutralHumanoid humanoid) {
        if (getDriver != null) {
            try {
                Object driver = getDriver.invoke(vehicle);
                if (driver != null) {
                    return driver == humanoid;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        if (seatsField == null || seatPassengerIdField == null) {
            return false;
        }
        try {
            Object seats = seatsField.get(vehicle);
            if (!(seats instanceof List<?> list) || list.isEmpty()) {
                return false;
            }
            Object passenger = seatPassengerIdField.get(list.get(0));
            return passenger instanceof Integer id && id == humanoid.getId();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean isPlayerOperated(Object control) {
        if (getOperator == null) {
            return false;
        }
        try {
            return getOperator.invoke(control) instanceof Player;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static void write(Object control, boolean forward, boolean backward,
            boolean left, boolean right, boolean handbrake) throws IllegalAccessException {
        forwardField.setBoolean(control, forward);
        backwardField.setBoolean(control, backward);
        leftField.setBoolean(control, left);
        rightField.setBoolean(control, right);
        handbrakeField.setBoolean(control, handbrake);
        if (leftYawField != null) {
            leftYawField.setBoolean(control, false);
        }
        if (rightYawField != null) {
            rightYawField.setBoolean(control, false);
        }
        if (yRotKeepField != null) {
            yRotKeepField.setBoolean(control, false);
        }
        if (downField != null) {
            downField.setBoolean(control, false);
        }
        if (xRotKeepField != null) {
            xRotKeepField.setBoolean(control, false);
        }
    }

    private static void stopAll(Object control) throws IllegalAccessException {
        write(control, false, false, false, false, true);
    }

    private static void parkFlight(Object control) throws IllegalAccessException {
        write(control, false, false, false, false, false);
        if (yRotKeepField != null) {
            yRotKeepField.setBoolean(control, true);
        }
        if (xRotKeepField != null) {
            xRotKeepField.setBoolean(control, true);
        }
    }

    private static void centreSticks(Object control) throws IllegalAccessException {
        write(control, false, false, false, false, false);
    }

    private static void fly(Entity vehicle, Object control) throws IllegalAccessException {
        Flight flight = FLIGHTS.computeIfAbsent(vehicle.getId(), key -> new Flight());
        centreSticks(control);
        if (downField == null || yRotField == null) {
            return;
        }
        Player owner = vehicle.level().getNearestPlayer(vehicle, 256.0D);
        if (--flight.enemyTicks <= 0) {
            flight.enemy = enemyVisibleFromSky(vehicle, owner);
            flight.enemyTicks = 10;
        }
        if (flight.enemy != null && flight.enemy.isAlive()) {
            escort(vehicle, owner, control, flight);
        } else {
            land(vehicle, owner, control, flight);
        }
    }

    private static LivingEntity enemyVisibleFromSky(Entity vehicle, Player owner) {
        Vec3 center = owner != null ? owner.position() : vehicle.position();
        AABB area = new AABB(center, center).inflate(ENEMY_RANGE);
        for (LivingEntity candidate : vehicle.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity instanceof Enemy && entity.isAlive() && !entity.isSpectator())) {
            Vec3 fromSky = candidate.position().add(0.0D, 40.0D, 0.0D);
            if (HumanoidSight.canSee(vehicle.level(), vehicle, fromSky, candidate.getEyePosition())) {
                return candidate;
            }
        }
        return null;
    }

    private static void escort(Entity vehicle, Player owner, Object control, Flight flight)
            throws IllegalAccessException {
        flight.orbit += ORBIT_STEP;
        if (flight.orbit > Math.PI * 2.0D) {
            flight.orbit -= Math.PI * 2.0D;
        }
        double centerX = owner != null ? owner.getX() : vehicle.getX();
        double centerZ = owner != null ? owner.getZ() : vehicle.getZ();
        double targetX = centerX + Math.cos(flight.orbit) * ESCORT_RADIUS;
        double targetZ = centerZ + Math.sin(flight.orbit) * ESCORT_RADIUS;
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

    private static void land(Entity vehicle, Player owner, Object control, Flight flight)
            throws IllegalAccessException {
        BlockPos pad = flight.pad;
        if (pad == null || !padValid(vehicle.level(), pad)) {
            pad = findPad(vehicle, owner);
            flight.pad = pad;
            if (pad == null) {
                setPitch(control, 0.0D);
                holdAltitude(control, vehicle,
                        surfaceY(vehicle, vehicle.getX(), vehicle.getZ()) + ESCORT_ALTITUDE);
                return;
            }
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
            flight.pad = null;
            flight.orbit = 0.0D;
            return;
        }
        double sink = -vehicle.getDeltaMovement().y;
        if (sink > MAX_SINK) {
            handbrakeField.setBoolean(control, true);
        } else if (downField != null) {
            downField.setBoolean(control, true);
        }
    }

    private static void setHeading(Object control, float yaw) throws IllegalAccessException {
        if (yRotField == null) {
            return;
        }
        yRotField.setFloat(control, yaw);
        if (yRotKeepField != null) {
            yRotKeepField.setBoolean(control, false);
        }
        if (leftYawField != null) {
            leftYawField.setBoolean(control, false);
        }
        if (rightYawField != null) {
            rightYawField.setBoolean(control, false);
        }
    }

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
        }
    }

    private static void holdAltitude(Object control, Entity vehicle, double targetY)
            throws IllegalAccessException {
        double dy = targetY - vehicle.getY();
        double vy = vehicle.getDeltaMovement().y;
        if (dy > 1.5D || vy < -0.10D) {
            handbrakeField.setBoolean(control, true);
        } else if (dy < -1.5D || vy > 0.10D) {
            if (downField != null) {
                downField.setBoolean(control, true);
            }
        }
    }

    private static float headingTo(Entity vehicle, double x, double z) {
        double dx = x - vehicle.getX();
        double dz = z - vehicle.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static boolean blockedAhead(Entity vehicle, float heading) {
        double radians = Math.toRadians(heading);
        Vec3 from = vehicle.position().add(0.0D, 0.5D, 0.0D);
        Vec3 to = from.add(-Math.sin(radians) * PROBE_RANGE, 0.0D, Math.cos(radians) * PROBE_RANGE);
        BlockHitResult hit = vehicle.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vehicle));
        return hit.getType() != HitResult.Type.MISS;
    }

    private static double surfaceY(Entity vehicle, double x, double z) {
        BlockPos pos = vehicle.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(x, 0.0D, z));
        return pos.getY();
    }

    private static BlockPos findPad(Entity vehicle, Player owner) {
        Level level = vehicle.level();
        BlockPos center = owner != null ? owner.blockPosition() : vehicle.blockPosition();
        for (int radius = 8; radius <= (int) LAND_SEARCH_RADIUS; radius += 4) {
            for (int angle = 0; angle < 360; angle += 30) {
                int dx = (int) Math.round(Math.cos(Math.toRadians(angle)) * radius);
                int dz = (int) Math.round(Math.sin(Math.toRadians(angle)) * radius);
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        center.offset(dx, 0, dz));
                if (padValid(level, surface)) {
                    return surface;
                }
            }
        }
        return null;
    }

    private static boolean padValid(Level level, BlockPos pad) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos feet = pad.offset(dx, 0, dz);
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
                    return false;
                }
                BlockPos floor = feet.below();
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                        || !level.getBlockState(floor).getFluidState().isEmpty()) {
                    return false;
                }
                for (int up = 1; up <= 2; up++) {
                    BlockPos above = feet.above(up);
                    if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                        return false;
                    }
                }
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        feet.getX(), feet.getZ());
                if (Math.abs(height - pad.getY()) > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double clearanceAhead(Entity vehicle, float heading, double max) {
        double radians = Math.toRadians(heading);
        Vec3 direction = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        return HullTerrain.clearance(vehicle, vehicle.getBoundingBox(), direction, max, stepHeight(vehicle));
    }

    private static boolean groundAhead(Entity vehicle, float heading) {
        double radians = Math.toRadians(heading);
        Vec3 direction = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        AABB box = vehicle.getBoundingBox();
        for (double distance = 3.0D; distance <= 9.0D; distance += 3.0D) {
            AABB moved = box.move(direction.x * distance, 0.0D, direction.z * distance);
            if (!HullTerrain.groundUnder(vehicle, moved, 2.0D)) {
                return false;
            }
        }
        return true;
    }

    private static boolean entityAhead(Entity vehicle, Player owner, LivingEntity target) {
        double radians = Math.toRadians(vehicle.getYRot());
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double width = Math.max(0.8D, vehicle.getBbWidth() * 0.9D);
        AABB box = new AABB(-width, -2.0D, -width, width, 4.0D, width)
                .move(vehicle.position().add(forwardX * (1.0D + width), 0.0D,
                        forwardZ * (1.0D + width)));
        for (Entity other : vehicle.level().getEntities(vehicle, box)) {
            if (other == vehicle || other.getVehicle() == vehicle || other.isSpectator()) {
                continue;
            }
            double distance = other.distanceToSqr(vehicle);
            if (distance > 9.0D) {
                if (other == owner || other == target) {
                    continue;
                }
                if (owner != null && other == owner.getVehicle()) {
                    continue;
                }
                if (target != null && other == target.getVehicle()) {
                    continue;
                }
            }
            if (other instanceof LivingEntity) {
                return true;
            }
            if (other.getClass().getName().toLowerCase(Locale.ROOT).contains("vehicle")) {
                return true;
            }
        }
        return false;
    }

    private static boolean rearClear(Entity vehicle) {
        return clearanceAhead(vehicle, vehicle.getYRot() + 180.0F, 5.0D) > 2.0D;
    }

    private static boolean rearUsable(Entity vehicle) {
        return rearClear(vehicle) && groundAhead(vehicle, vehicle.getYRot() + 180.0F);
    }

    private static double stepHeight(Entity vehicle) {
        return isTracked(vehicle) ? 2.0D : 1.0D;
    }

    private static double horizontalSpeed(Entity vehicle) {
        Vec3 motion = vehicle.getDeltaMovement();
        return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
    }

    private static double horizontalDistance(Entity vehicle, Vec3 point) {
        double dx = point.x - vehicle.getX();
        double dz = point.z - vehicle.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double yawErrorTo(Entity vehicle, Vec3 aim) {
        double dx = aim.x - vehicle.getX();
        double dz = aim.z - vehicle.getZ();
        float desired = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Mth.wrapDegrees(desired - vehicle.getYRot());
    }

    private static double weaponRange(Entity vehicle) {
        double best = 0.0D;
        for (Object unit : VehicleAim.seatUnits(vehicle)) {
            try {
                Field field = unit.getClass().getField("weaponHitRange");
                best = Math.max(best, field.getDouble(unit));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return best;
    }

    private static boolean isTracked(Entity vehicle) {
        return superclassNamed(vehicle, "Tracked");
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

    private static void ensureEngine(Entity vehicle) {
        try {
            if (isEngineOn != null && toggleEngine != null && !(Boolean) isEngineOn.invoke(vehicle)) {
                toggleEngine.invoke(vehicle, Boolean.TRUE);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String describeDriver(NeutralHumanoid humanoid) {
        Entity vehicle = humanoid.getVehicle();
        if (vehicle == null || !resolve()) {
            return "drive=none";
        }
        try {
            State state = STATES.get(humanoid.getId());
            Player owner = findOwner(humanoid);
            double ownerDistance = owner == null ? -1.0D : Math.sqrt(humanoid.distanceToSqr(owner));
            String mode = humanoid.getTarget() != null ? "target" : owner != null ? "follow" : "idle";
            return "drive=" + isDriver(vehicle, humanoid)
                    + " mode=" + mode
                    + " ownerDist=" + Math.round(ownerDistance)
                    + " recover=" + (state == null ? 0 : state.recoverTicks)
                    + " aheadClear=" + Math.round(clearanceAhead(vehicle, vehicle.getYRot(), PROBE_RANGE));
        } catch (RuntimeException exception) {
            return "drive=error";
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return !broken;
        }
        resolved = true;
        try {
            Class<?> vehicleClass = Class.forName(VEHICLE_CLASS);
            controlUnitField = vehicleClass.getField("controlUnit");
            Class<?> controlClass = Class.forName(CONTROL_CLASS);
            forwardField = controlClass.getField("forward");
            backwardField = controlClass.getField("backward");
            leftField = controlClass.getField("left");
            rightField = controlClass.getField("right");
            handbrakeField = controlClass.getField("up");
            leftYawField = optionalField(controlClass, "leftYaw");
            rightYawField = optionalField(controlClass, "rightYaw");
            yRotField = optionalField(controlClass, "yRot");
            yRotKeepField = optionalField(controlClass, "yRotKeep");
            downField = optionalField(controlClass, "down");
            xRotField = optionalField(controlClass, "xRot");
            xRotKeepField = optionalField(controlClass, "xRotKeep");
            resetControl = optionalMethod(controlClass, "reset");
            getOperator = optionalMethod(controlClass, "getOperator");
            seatsField = optionalField(vehicleClass, "seats");
            getDriver = optionalMethod(vehicleClass, "getDriver");
            toggleEngine = optionalMethod(vehicleClass, "toggleEngine", Boolean.class);
            isEngineOn = optionalMethod(vehicleClass, "isEngineOn");
            try {
                seatPassengerIdField = optionalField(Class.forName(VEHICLE_CLASS + "$Seat"), "passengerId");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                seatPassengerIdField = null;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            return false;
        }
    }

    private static Field optionalField(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }

    private static Method optionalMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
