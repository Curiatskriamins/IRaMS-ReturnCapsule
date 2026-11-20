package com.curiats.returncapsule.compat.vehicle;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleGunner {

    private static final String VEHICLE_CLASS = "org.ywzj.vehicle.entity.vehicle.AbstractVehicle";
    private static final String WEAPON_UNIT_CLASS = "org.ywzj.vehicle.vehicle.part.WeaponUnit";
    private static final float YAW_TOLERANCE = 2.0F;
    private static final float FAIL_OPEN_YAW = 6.0F;

    /** Main cannon targets below this max health are not worth a shell (maggots, swarmers). */
    private static final double MAIN_CANNON_MIN_HEALTH = 50.0D;

    private static final int MACHINE_GUN_SETTLE = 3;

    private static final int REPOSITION_TICKS = 60;

    private static final Map<Integer, Long> LAST_ORDER = new ConcurrentHashMap<>();

    private static final Map<String, Long> SHOT_CLOCK = new ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.AtomicLong GUN_LOGS =
            new java.util.concurrent.atomic.AtomicLong();

    private static final Map<Integer, Integer> AIM_TICKS = new ConcurrentHashMap<>();

    private static final int AIM_SETTLE = 6;
    private static final int FAIL_OPEN_AFTER = 30;

    private static final int FAIL_OPEN_TICKS = 60;

    private static boolean resolved;
    private static boolean broken;
    private static Field seatsField;
    private static Field partUnitField;
    private static Method getOwner;
    private static Method aim;
    private static Method aimContext;
    private static Method getCurrentWeapon;
    private static Method getCurrentSecondaryWeapon;
    private static Method shoot;
    private static Method getOwnOperatorUnit;

    private static Method getRootParentWeaponUnit;

    private static Method toggleEngine;
    private static Method isEngineOn;

    private static Class<?> weaponUnitClass;

    private static java.lang.reflect.Field seatPassengerIdField;

    private static java.lang.reflect.Field weaponBayUnits;
    private static Method changeSeat;
    private static Method getIndex;
    private static Method getShootInterval;
    private static Method getWeaponData;
    private static Method getVelocity;
    private static Method setOwner;
    private static Method getGravity;

    private VehicleGunner() {
    }

    private static Object rootUnit(Object unit) {
        if (getRootParentWeaponUnit == null) {
            return unit;
        }
        try {
            Object root = getRootParentWeaponUnit.invoke(unit);
            return root != null ? root : unit;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return unit;
        }
    }

    private static boolean seatFree(Object seat) {
        try {
            Object id = seatPassengerIdField.get(seat);
            return !(id instanceof Integer passengerId) || passengerId < 0;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    public static void tick(NeutralHumanoid humanoid) {
        if (humanoid.level().isClientSide || humanoid.isDowned()) {
            return;
        }
        Entity vehicle = humanoid.getVehicle();
        if (vehicle == null || !resolve()) {
            return;
        }
        ensureEngine(vehicle);
        List<Object> units = weaponUnitsFor(humanoid, vehicle);
        LivingEntity target = humanoid.getTarget();
        if (target == null) {
            target = nearestEnemy(humanoid);
        }
        if (target == null) {
            AIM_TICKS.clear();
            returnToFront(humanoid, units);
            return;
        }
        if (friendlyInLine(humanoid, target)) {
            returnToFront(humanoid, units);
            return;
        }
        for (Object unit : units) {
            if (!controlledBy(unit, humanoid)) {
                // The turret belongs to somebody else (for example the player driving), and its direction follows that
                // operator's controls. Aiming it would fight them and firing it blind is worse than staying silent.
                continue;
            }
            bindOwner(unit, humanoid);
            Object owner = rootUnit(unit);
            boolean line = lineOfFire(vehicle, owner, target);
            Vec3 aimPoint = leadPoint(owner, target);
            boolean worthCannon = target.getMaxHealth() >= MAIN_CANNON_MIN_HEALTH;
            if (!line && worthCannon) {
                // No line for the cannon: ask the driver for a firing position instead of sitting there.
                requestReposition(vehicle, target);
            }
            if (GUN_LOGS.incrementAndGet() <= 600) {
                com.curiats.returncapsule.ReturnCapsuleMod.LOGGER.info(
                        "[gun] target={} dist={} line={} settle={} aligned={} yawErr={}",
                        target.getName().getString(), String.format("%.1f", vehicle.distanceTo(target)), line,
                        AIM_TICKS.getOrDefault(System.identityHashCode(unit), 0), aligned(owner, aimPoint),
                        String.format("%.1f", yawError(owner, aimPoint)));
            }
            try {
                aim.invoke(unit, aimPoint);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                continue;
            }
            int settle = AIM_TICKS.merge(System.identityHashCode(unit), 1, Integer::sum);
            // Machine guns fire on anything that moves, line or no line: they are for suppression, not precision.
            if (getCurrentSecondaryWeapon != null && settle >= MACHINE_GUN_SETTLE) {
                fire(unit, humanoid, getCurrentSecondaryWeapon, true);
            }
            if (!line || !worthCannon || settle < AIM_SETTLE) {
                continue;
            }
            if (!aligned(owner, aimPoint)) {
                // A turret that never converges must not spray rounds at whatever it happens to be pointing at, but
                // waiting for a perfect two degree solution while the vehicle moves made the gun far too quiet. Once
                // the target has been tracked for a while and the barrel is close, fire rather than never fire.
                if (settle < FAIL_OPEN_AFTER || yawError(owner, aimPoint) > FAIL_OPEN_YAW) {
                    continue;
                }
            }
            fire(owner, humanoid, getCurrentWeapon, false);
        }
    }

    /**
     * Aim lead: the turret points where the target will be when the round gets there.
     *
     * <p>Vehicle rounds carry a per tick {@code velocity} (10 by default, i.e. 200 m/s), so even a walking target
     * needs a lead of a few ticks at range. Aiming at the current eye position made cannon shells and especially
     * preset-guidance missiles land behind the target, which read as "the missiles miss".</p>
     */
    private static Vec3 leadPoint(Object unit, LivingEntity target) {
        Vec3 aim = target.getEyePosition();
        Vec3 origin = VehicleAim.unitPosition(unit);
        double velocity = weaponVelocity(unit);
        if (origin == null || velocity <= 0.0D) {
            return aim;
        }
        Vec3 motion = target.getDeltaMovement();
        double gravity = weaponGravity(unit);
        for (int iteration = 0; iteration < 3; iteration++) {
            double flight = origin.distanceTo(aim) / velocity;
            if (flight > 100.0D) {
                break;
            }
            // Shells and rockets drop, so the lead point also rises by the drop over the flight time. Without this the
            // rounds landed short at range even when the turret was perfectly on target.
            double drop = 0.5D * gravity * flight * flight;
            aim = target.getEyePosition().add(motion.scale(flight)).add(0.0D, drop, 0.0D);
        }
        return aim;
    }

    private static double weaponGravity(Object unit) {
        if (getGravity == null || getWeaponData == null || getCurrentWeapon == null) {
            return 0.0D;
        }
        try {
            Object optional = getCurrentWeapon.invoke(unit);
            if (!(optional instanceof Optional<?> present) || present.isEmpty()) {
                return 0.0D;
            }
            Object data = getWeaponData.invoke(present.get());
            Object value = data == null ? null : getGravity.invoke(data);
            return value instanceof Number number ? Math.max(0.0D, number.doubleValue()) : 0.0D;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return 0.0D;
        }
    }

    private static float yawError(Object unit, Vec3 aim) {
        float yaw = VehicleAim.turretYaw(unit);
        Vec3 origin = VehicleAim.unitPosition(unit);
        if (Float.isNaN(yaw) || origin == null) {
            return Float.MAX_VALUE;
        }
        float desired = (float) Math.toDegrees(Math.atan2(-(aim.x - origin.x), aim.z - origin.z));
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(desired - yaw));
    }

    private static double weaponVelocity(Object unit) {
        if (getWeaponData == null || getVelocity == null || getCurrentWeapon == null) {
            return 0.0D;
        }
        try {
            Object optional = getCurrentWeapon.invoke(unit);
            if (!(optional instanceof Optional<?> present) || present.isEmpty()) {
                return 0.0D;
            }
            Object data = getWeaponData.invoke(present.get());
            Object value = data == null ? null : getVelocity.invoke(data);
            return value instanceof Number number ? Math.max(0.0D, number.doubleValue()) : 0.0D;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return 0.0D;
        }
    }

    private static boolean controlledBy(Object unit, NeutralHumanoid humanoid) {
        if (getOwner == null) {
            return true;
        }
        try {
            Object owner = getOwner.invoke(unit);
            return owner == null || owner == humanoid;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    /**
     * Vehicle weapons used to fire at any target in range, including through city walls; the turret now needs an
     * unobstructed line from the weapon mount before it aims or shoots.
     */
    private static boolean lineOfFire(Entity vehicle, Object unit, LivingEntity target) {
        Vec3 from = VehicleAim.unitPosition(unit);
        if (from == null) {
            from = vehicle.position().add(0.0D, vehicle.getBbHeight() * 0.7D, 0.0D);
        }
        return com.curiats.returncapsule.entity.humanoid.HumanoidSight.canSee(
                vehicle.level(), vehicle, from, target.getEyePosition());
    }

    private static boolean aiming(Object unit) {
        return !Float.isNaN(VehicleAim.turretYaw(unit));
    }

    private static void ensureEngine(Entity vehicle) {
        try {
            if (isEngineOn != null && toggleEngine != null && !(Boolean) isEngineOn.invoke(vehicle)) {
                toggleEngine.invoke(vehicle, Boolean.TRUE);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static boolean aligned(Object unit, Vec3 aim) {
        float yaw = VehicleAim.turretYaw(unit);
        net.minecraft.world.phys.Vec3 origin = VehicleAim.unitPosition(unit);
        if (Float.isNaN(yaw) || origin == null) {
            return false;
        }
        float desired = (float) Math.toDegrees(Math.atan2(-(aim.x - origin.x), aim.z - origin.z));
        float yawError = Math.abs(net.minecraft.util.Mth.wrapDegrees(desired - yaw));
        if (yawError > YAW_TOLERANCE) {
            return false;
        }
        float pitch = VehicleAim.requiredPitch(unit, aim);
        return pitch <= VehicleAim.maxPitch(unit) + 1.0F
                && pitch >= VehicleAim.minPitch(unit) - 1.0F;
    }

    private static boolean friendlyInLine(NeutralHumanoid humanoid, LivingEntity target) {
        net.minecraft.world.entity.player.Player player = humanoid.level().getNearestPlayer(humanoid, 32.0D);
        if (player == null) {
            return false;
        }
        if (player == target) {
            return true;
        }
        if (player.getVehicle() != null && player.getVehicle() == humanoid.getVehicle()) {
            return false;
        }
        net.minecraft.world.phys.Vec3 from = humanoid.position();
        net.minecraft.world.phys.Vec3 to = target.getEyePosition();
        net.minecraft.world.phys.Vec3 point = player.getEyePosition();
        net.minecraft.world.phys.Vec3 segment = to.subtract(from);
        double length = segment.length();
        if (length < 1.0E-3D) {
            return false;
        }
        double t = point.subtract(from).dot(segment) / (length * length);
        t = Math.max(0.0D, Math.min(1.0D, t));
        net.minecraft.world.phys.Vec3 closest = from.add(segment.scale(t));
        double distance = closest.distanceTo(point);
        double radius = 3.0D + player.getBbWidth();
        return distance < radius && t > 0.25D && t < 0.85D;
    }

    /** Asks the driver for a hull position that can actually see the target with the cannon. */
    private static void requestReposition(Entity vehicle, LivingEntity target) {
        long now = vehicle.level().getGameTime();
        Long last = LAST_ORDER.get(vehicle.getId());
        if (last != null && now - last < REPOSITION_TICKS) {
            return;
        }
        for (int step = 0; step < 8; step++) {
            double angle = Math.toRadians(step * 45.0D);
            Vec3 candidate = new Vec3(
                    vehicle.getX() + Math.cos(angle) * 12.0D,
                    vehicle.getY(),
                    vehicle.getZ() + Math.sin(angle) * 12.0D);
            net.minecraft.world.phys.AABB box = vehicle.getBoundingBox().move(
                    candidate.x - vehicle.getX(), 0.0D, candidate.z - vehicle.getZ());
            if (VehicleTerrain.boxBlocked(vehicle.level(), box, 2.0D)) {
                continue;
            }
            Vec3 eye = candidate.add(0.0D, vehicle.getBbHeight() * 0.7D, 0.0D);
            if (!com.curiats.returncapsule.entity.humanoid.HumanoidSight.canSee(
                    vehicle.level(), vehicle, eye, target.getEyePosition())) {
                continue;
            }
            VehicleOrders.reposition(vehicle, candidate, REPOSITION_TICKS);
            LAST_ORDER.put(vehicle.getId(), now);
            return;
        }
    }

    private static void fire(Object unit, NeutralHumanoid humanoid, Method source, boolean secondary) {
        try {
            Object optional = source.invoke(unit);
            if (!(optional instanceof Optional<?> present) || present.isEmpty()) {
                return;
            }
            Object weapon = present.get();
            long interval = getShootInterval == null ? 250L
                    : ((Number) getShootInterval.invoke(weapon)).longValue();
            long now = System.currentTimeMillis();
            String key = System.identityHashCode(unit) + (secondary ? ":s" : ":p");
            int index = getIndex == null ? 0 : ((Number) getIndex.invoke(weapon)).intValue();
            Object context = aimContext.invoke(unit);
            if (now - SHOT_CLOCK.getOrDefault(key, 0L) < interval) {
                return;
            }
            SHOT_CLOCK.put(key, now);
            shoot.invoke(unit, index, List.of(context), humanoid);
            humanoid.markVehicleShot(now);
        } catch (ReflectiveOperationException | RuntimeException exception) {
        }
    }

    public static String describe(NeutralHumanoid humanoid) {
        Entity vehicle = humanoid.getVehicle();
        if (vehicle == null) {
            return "vehicle=none";
        }
        if (!resolve()) {
            return "vehicle=" + vehicle.getClass().getSimpleName() + " resolved=false";
        }
        ensureEngine(vehicle);
        List<Object> units = weaponUnitsFor(humanoid, vehicle);
        StringBuilder text = new StringBuilder("vehicle=")
                .append(vehicle.getClass().getSimpleName())
                .append(" units=").append(units.size());
        for (Object unit : units) {
            try {
                Object optional = getCurrentWeapon.invoke(unit);
                boolean present = optional instanceof java.util.Optional<?> value && value.isPresent();
                text.append(" [settle=").append(AIM_TICKS.getOrDefault(System.identityHashCode(unit), 0))
                        .append(" weapon=").append(present).append(']');
            } catch (ReflectiveOperationException | RuntimeException exception) {
                text.append(" [error]");
            }
        }
        return text.toString();
    }

    private static boolean weaponBayClosed(Object unit, Object weapon) {
        if (weaponBayUnits == null) {
            return false;
        }
        try {
            Object map = weaponBayUnits.get(unit);
            if (!(map instanceof Map<?, ?> bays)) {
                return false;
            }
            Object bay = bays.get(weapon);
            if (bay == null) {
                return false;
            }
            Object on = bay.getClass().getMethod("isOn").invoke(bay);
            return on instanceof Boolean value && !value;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static void returnToFront(NeutralHumanoid humanoid, List<Object> units) {
        Entity vehicle = humanoid.getVehicle();
        if (vehicle == null || units.isEmpty()) {
            return;
        }
        float yaw = (float) Math.toRadians(vehicle.getYRot());
        Vec3 front = vehicle.position()
                .add(-Math.sin(yaw) * 24.0D, 0.0D, Math.cos(yaw) * 24.0D)
                .add(0.0D, vehicle.getBbHeight() * 0.5D, 0.0D);
        for (Object unit : units) {
            try {
                aim.invoke(unit, front);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return;
            }
        }
    }

    private static LivingEntity nearestEnemy(NeutralHumanoid humanoid) {
        LivingEntity best = null;
        double bestDistance = 48.0D * 48.0D;
        for (LivingEntity candidate : humanoid.level().getEntitiesOfClass(LivingEntity.class,
                humanoid.getBoundingBox().inflate(48.0D),
                entity -> entity instanceof net.minecraft.world.entity.monster.Enemy
                        && entity.isAlive() && !entity.isSpectator())) {
            double distance = humanoid.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static List<Object> weaponUnitsFor(NeutralHumanoid humanoid, Entity vehicle) {
        List<Object> found = new ArrayList<>();
        try {
            Object seats = seatsField.get(vehicle);
            if (!(seats instanceof List<?> list)) {
                return found;
            }
            boolean driving = isDriving(humanoid, list);
            // The seat we are sitting in comes first: the driver can shoot too, and on many vehicles the driver
            // seat's part unit is the weapon the pilot actually controls.
            if (getOwnOperatorUnit != null) {
                Object own = getOwnOperatorUnit.invoke(vehicle, humanoid);
                if (own != null && weaponUnitClass.isInstance(own)) {
                    bindOwner(own, humanoid);
                    found.add(own);
                }
            }
            Object claimable = null;
            int claimableSeat = -1;
            for (int index = 0; index < list.size(); index++) {
                Object unit = partUnitField.get(list.get(index));
                if (unit == null || !weaponUnitClass.isInstance(unit)) {
                    continue;
                }
                Object owner = getOwner.invoke(unit);
                if (owner == humanoid) {
                    if (!found.contains(unit)) {
                        found.add(unit);
                    }
                } else if (seatFree(list.get(index))) {
                    if (driving) {
                        // This vehicle lets the driver work the main gun, which lives on its own seat's part unit.
                        // The driver never left that seat, so the unit has no owner and stayed silent: adopt it here.
                        bindOwner(unit, humanoid);
                        if (!found.contains(unit)) {
                            found.add(unit);
                        }
                    } else if (claimable == null) {
                        claimable = unit;
                        claimableSeat = index;
                    }
                }
            }
            // Only look for another seat when we are not the driver: abandoning the wheel to man a turret used to
            // leave the vehicle stopped and the driver seat empty.
            if (found.isEmpty() && claimable != null) {
                changeSeat.invoke(vehicle, humanoid, claimableSeat);
                found.add(claimable);
            }
            if (found.isEmpty()) {
                for (Object seat : list) {
                    Object unit = partUnitField.get(seat);
                    if (unit != null && weaponUnitClass.isInstance(unit)) {
                        found.add(unit);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
        }
        return found;
    }

    /**
     * A weapon unit only stabilises and rotates toward {@code aim(...)} while it has an owner ({@code PartUnit.owner},
     * which the mod sets when a player takes the seat). An AI boarder never goes through that path, so its turret stayed
     * inert and never aligned - the gunner looked like it refused to use its own seat weapon. Claiming an unowned unit
     * fixes both the aim and the ownership check.
     */
    private static void bindOwner(Object unit, NeutralHumanoid humanoid) {
        if (setOwner == null) {
            return;
        }
        try {
            Object owner = getOwner == null ? null : getOwner.invoke(unit);
            if (owner == null) {
                setOwner.invoke(unit, humanoid);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // the unit keeps its current owner
        }
    }

    private static boolean isDriving(NeutralHumanoid humanoid, List<?> seats) {
        if (seats.isEmpty()) {
            return false;
        }
        try {
            Object id = seatPassengerIdField.get(seats.get(0));
            return id instanceof Integer passengerId && passengerId == humanoid.getId();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return !broken;
        }
        resolved = true;
        try {
            Class<?> vehicleClass = Class.forName(VEHICLE_CLASS);
            seatsField = vehicleClass.getField("seats");
            Class<?> seatClass = Class.forName(VEHICLE_CLASS + "$Seat");
            partUnitField = seatClass.getField("partUnit");
            Class<?> unitClass = Class.forName(WEAPON_UNIT_CLASS);
            weaponUnitClass = unitClass;
            seatPassengerIdField = Class.forName(WEAPON_UNIT_CLASS.replace("vehicle.part.WeaponUnit",
                    "entity.vehicle.AbstractVehicle$Seat")).getField("passengerId");
            getOwner = unitClass.getMethod("getOwner");
            aim = unitClass.getMethod("aim", Vec3.class);
            aimContext = unitClass.getMethod("aimContext");
            getCurrentWeapon = unitClass.getMethod("getCurrentWeapon");
            try {
                getCurrentSecondaryWeapon = unitClass.getMethod("getCurrentSecondaryWeapon");
            } catch (NoSuchMethodException ignored) {
                getCurrentSecondaryWeapon = null;
            }
            shoot = unitClass.getMethod("shoot", int.class, List.class, LivingEntity.class);
            changeSeat = vehicleClass.getMethod("changeSeat", LivingEntity.class, int.class);
            try {
                toggleEngine = vehicleClass.getMethod("toggleEngine", Boolean.class);
                isEngineOn = vehicleClass.getMethod("isEngineOn");
            } catch (NoSuchMethodException ignored) {
                toggleEngine = null;
                isEngineOn = null;
            }
            try {
                getRootParentWeaponUnit = unitClass.getMethod("getRootParentWeaponUnit");
            } catch (NoSuchMethodException ignored) {
                getRootParentWeaponUnit = null;
            }
            try {
                getOwnOperatorUnit = vehicleClass.getMethod("getOwnOperatorUnit", LivingEntity.class);
            } catch (NoSuchMethodException ignored) {
                getOwnOperatorUnit = null;
            }
            try {
                weaponBayUnits = unitClass.getField("weaponBayUnits");
            } catch (NoSuchFieldException ignored) {
                weaponBayUnits = null;
            }
            Class<?> weaponClass = Class.forName("org.ywzj.vehicle.vehicle.weapon.AbstractVehicleWeapon");
            getIndex = weaponClass.getMethod("getIndex");
            getShootInterval = weaponClass.getMethod("getShootInterval");
            getWeaponData = weaponClass.getMethod("getData");
            try {
                setOwner = Class.forName("org.ywzj.vehicle.vehicle.part.PartUnit")
                        .getMethod("setOwner", LivingEntity.class);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                setOwner = null;
            }
            try {
                getVelocity = Class.forName("org.ywzj.vehicle.custom.weapon.data.BaseVehicleWeaponData")
                        .getMethod("getVelocity");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                getVelocity = null;
            }
            try {
                getGravity = Class.forName("org.ywzj.vehicle.custom.weapon.data.BaseVehicleWeaponData")
                        .getMethod("getGravity");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                getGravity = null;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            return false;
        }
    }
}
