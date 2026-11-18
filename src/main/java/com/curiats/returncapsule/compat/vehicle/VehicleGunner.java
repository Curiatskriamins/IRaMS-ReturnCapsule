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
    private static final float YAW_TOLERANCE = 3.0F;

    private static final Map<String, Long> SHOT_CLOCK = new ConcurrentHashMap<>();

    private static final Map<Integer, Integer> AIM_TICKS = new ConcurrentHashMap<>();

    private static final int AIM_SETTLE = 12;

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
            try {
                aim.invoke(unit, target.getEyePosition());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                continue;
            }
            int settle = AIM_TICKS.merge(System.identityHashCode(unit), 1, Integer::sum);
            if (settle < AIM_SETTLE) {
                continue;
            }
            Object owner = rootUnit(unit);
            if (!aligned(owner, target) && settle < FAIL_OPEN_TICKS) {
                continue;
            }
            fire(owner, humanoid, getCurrentWeapon, false);
            if (getCurrentSecondaryWeapon != null) {
                fire(unit, humanoid, getCurrentSecondaryWeapon, true);
            }
        }
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

    private static boolean aligned(Object unit, LivingEntity target) {
        float yaw = VehicleAim.turretYaw(unit);
        net.minecraft.world.phys.Vec3 origin = VehicleAim.unitPosition(unit);
        if (Float.isNaN(yaw) || origin == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 aim = target.getEyePosition();
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
            if (weaponBayClosed(unit, weapon)) {
                shoot.invoke(unit, index, List.of(context), humanoid);
                return;
            }
            if (now - SHOT_CLOCK.getOrDefault(key, 0L) < interval) {
                return;
            }
            shoot.invoke(unit, index, List.of(context), humanoid);
            SHOT_CLOCK.put(key, now);
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
            Object claimable = null;
            int claimableSeat = -1;
            for (int index = 0; index < list.size(); index++) {
                Object unit = partUnitField.get(list.get(index));
                if (unit == null || !weaponUnitClass.isInstance(unit)) {
                    continue;
                }
                Object owner = getOwner.invoke(unit);
                if (owner == humanoid) {
                    found.add(unit);
                } else if (claimable == null && seatFree(list.get(index))) {
                    claimable = unit;
                    claimableSeat = index;
                }
            }
            if (found.isEmpty() && claimable != null) {
                changeSeat.invoke(vehicle, humanoid, claimableSeat);
                found.add(claimable);
            }
            if (found.isEmpty() && getOwnOperatorUnit != null) {
                Object own = getOwnOperatorUnit.invoke(vehicle, humanoid);
                if (own != null && weaponUnitClass.isInstance(own)) {
                    found.add(own);
                }
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
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            return false;
        }
    }
}
