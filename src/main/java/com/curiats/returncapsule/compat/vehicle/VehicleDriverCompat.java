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
    private static Field seatsField;
    private static java.lang.reflect.Method toggleEngine;
    private static java.lang.reflect.Method isEngineOn;

    private static final java.util.Map<Integer, Integer> FLANK_ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, Integer> FLANK_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, net.minecraft.world.phys.Vec3> FLANK_AIM = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Integer> STUCK_TICKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, Integer> THROTTLE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Integer, Integer> RECOVERY = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int RECOVERY_TICKS = 1200;

    private static final int RECOVERY_PHASE = 60;

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
            LivingEntity target = humanoid.getTarget();
            net.minecraft.world.entity.player.Player owner = humanoid.level().getNearestPlayer(humanoid, 256.0D);
            boolean ownerAboard = owner != null && owner.getVehicle() == vehicle;
            net.minecraft.world.phys.Vec3 aim = target != null
                    ? target.position()
                    : owner != null && !ownerAboard && humanoid.distanceToSqr(owner) > 48.0D * 48.0D
                            ? owner.position() : null;
            if (aim == null) {
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
            net.minecraft.world.phys.Vec3 replayAim = replayAim(humanoid, vehicle, owner, forwardSpeed(vehicle), target != null);
            if (replayAim != null) {
                aim = replayAim;
            }
            double yawError = yawErrorTo(vehicle, aim);
            double rawDistance = vehicle.position().distanceTo(aim);
            double clearance = clearanceTo(vehicle, target, owner);
            double distance = rawDistance - clearance;
            double keep = target != null ? Math.max(MIN_ENGAGE_RANGE, weaponRange(vehicle))
                    : rawDistance > 128.0D ? 12.0D : 24.0D;
            double frontClear = cornerClearance(vehicle, yawError);
            double safeFront = vehicle.getBbWidth() * 0.5D + 2.0D;
            boolean groundAhead = groundAhead(vehicle);
            boolean forward = distance > keep && Math.abs(yawError) < 70.0D
                    && frontClear > safeFront && groundAhead;
            boolean backward = target != null ? distance < STOP_RANGE : distance < 9.0D;
            boolean complexTerrain = frontClear < 10.0D || !groundAhead
                    || Math.abs(yawError) > 22.0D || sideTight(vehicle);
            if (complexTerrain) {
                int throttle = THROTTLE.getOrDefault(humanoid.getId(), 0) + 1;
                THROTTLE.put(humanoid.getId(), throttle);
                if (isTracked(vehicle)) {
                    if (forward && (throttle % 4) >= 2) {
                        forward = false;
                    }
                } else if (forward && (throttle % 4) >= 2) {
                    forward = false;
                    backward = false;
                    handbrakeField.setBoolean(control, true);
                }
            } else {
                THROTTLE.remove(humanoid.getId());
            }
            int recovery = RECOVERY.getOrDefault(humanoid.getId(), 0);
            if (recovery > 0) {
                RECOVERY.put(humanoid.getId(), recovery - 1);
                int phase = (RECOVERY_TICKS - recovery) / RECOVERY_PHASE % 4;
                forward = phase >= 2;
                backward = phase < 2;
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
            if (target != null) {
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
            boolean left = false;
            boolean right = false;
            boolean handbrake = !forward && !backward;
            forwardField.setBoolean(control, forward);
            backwardField.setBoolean(control, backward);
            leftField.setBoolean(control, left);
            rightField.setBoolean(control, right);
            handbrakeField.setBoolean(control, handbrake);
            float desiredYaw = (float) Math.toDegrees(Math.atan2(-(aim.x - vehicle.getX()),
                    aim.z - vehicle.getZ()));
            leftYawField.setBoolean(control, yawError < -4.0D);
            rightYawField.setBoolean(control, yawError > 4.0D);
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
        double left = 0.0D;
        double right = 0.0D;
        for (net.minecraft.world.phys.Vec3 corner : corners(vehicle)) {
            left += rayLength(vehicle, corner, new net.minecraft.world.phys.Vec3(px, 0.0D, pz), PROBE_RANGE);
            right += rayLength(vehicle, corner, new net.minecraft.world.phys.Vec3(-px, 0.0D, -pz), PROBE_RANGE);
        }
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

    private static boolean sideTight(Entity vehicle) {
        net.minecraft.world.phys.Vec3 look = vehicle.getLookAngle();
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z).normalize();
        double px = -forward.z;
        double pz = forward.x;
        double total = 0.0D;
        for (net.minecraft.world.phys.Vec3 corner : corners(vehicle)) {
            total += rayLength(vehicle, corner, new net.minecraft.world.phys.Vec3(px, 0.0D, pz), 8.0D);
            total += rayLength(vehicle, corner, new net.minecraft.world.phys.Vec3(-px, 0.0D, -pz), 8.0D);
        }
        return total < 16.0D;
    }

    private static boolean groundAhead(Entity vehicle) {
        net.minecraft.world.phys.Vec3 look = vehicle.getLookAngle();
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z).normalize();
        boolean clear = true;
        for (net.minecraft.world.phys.Vec3 corner : corners(vehicle)) {
            for (double distance = 3.0D; distance <= 9.0D; distance += 3.0D) {
                net.minecraft.world.phys.Vec3 probe = corner.add(forward.scale(distance));
                net.minecraft.world.phys.Vec3 from = probe.add(0.0D, 2.0D, 0.0D);
                net.minecraft.world.phys.Vec3 to = probe.add(0.0D, -6.0D, 0.0D);
                net.minecraft.world.phys.BlockHitResult hit = vehicle.level().clip(new net.minecraft.world.level.ClipContext(
                        from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, vehicle));
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                    clear = false;
                    break;
                }
                double drop = corner.y - hit.getLocation().y;
                if (drop > 2.0D) {
                    clear = false;
                    break;
                }
            }
        }
        return clear;
    }

    private static double cornerClearance(Entity vehicle, double yawError) {
        net.minecraft.world.phys.Vec3 look = vehicle.getLookAngle();
        net.minecraft.world.phys.Vec3 forward = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z);
        if (Math.abs(yawError) > 20.0D) {
            return PROBE_RANGE;
        }
        double best = PROBE_RANGE;
        for (net.minecraft.world.phys.Vec3 corner : corners(vehicle)) {
            best = Math.min(best, rayLength(vehicle, corner, forward, PROBE_RANGE));
        }
        return best;
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
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            return false;
        }
    }
}
