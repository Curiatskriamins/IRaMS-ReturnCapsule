package com.curiats.returncapsule.compat.vehicle;

import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class VehicleAim {

    private static boolean resolved;
    private static boolean broken;
    private static Field seatsField;
    private static Field partUnitField;
    private static Field ySelfRotField;
    private static Method getCurrentWeapon;
    private static Method getData;
    private static Method getXRotMin;
    private static Method getXRotMax;
    private static Method worldSeatPosition;
    private static Method aimAtPosition;
    private static Method worldRot;

    private VehicleAim() {
    }

    public static float turretYaw(Object unit) {
        if (!resolve()) {
            return Float.NaN;
        }
        if (worldRot != null) {
            try {
                Object value = worldRot.invoke(unit);
                if (value != null) {
                    java.lang.reflect.Field yField = value.getClass().getField("y");
                    return yField.getFloat(value);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        if (ySelfRotField != null) {
            try {
                return ySelfRotField.getFloat(unit);
            } catch (IllegalAccessException exception) {
                return Float.NaN;
            }
        }
        return Float.NaN;
    }

    public static float minPitch(Object unit) {
        return limit(unit, getXRotMin, -90.0F);
    }

    public static float maxPitch(Object unit) {
        return limit(unit, getXRotMax, 90.0F);
    }

    private static float limit(Object unit, Method accessor, float fallback) {
        if (!resolve() || accessor == null || getCurrentWeapon == null || getData == null) {
            return fallback;
        }
        try {
            Object optional = getCurrentWeapon.invoke(unit);
            if (!(optional instanceof java.util.Optional<?> present) || present.isEmpty()) {
                return fallback;
            }
            Object weapon = present.get();
            Object data = getData.invoke(weapon);
            if (data == null) {
                return fallback;
            }
            Object value = accessor.invoke(data);
            return value instanceof Number number ? number.floatValue() : fallback;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return fallback;
        }
    }

    public static float requiredPitch(Object unit, Vec3 target) {
        float yaw = turretYaw(unit);
        if (Float.isNaN(yaw)) {
            return 0.0F;
        }
        Vec3 origin = unitPosition(unit);
        if (origin == null) {
            return 0.0F;
        }
        double horizontal = Math.sqrt((target.x - origin.x) * (target.x - origin.x)
                + (target.z - origin.z) * (target.z - origin.z));
        if (horizontal < 0.5D) {
            return 0.0F;
        }
        return (float) -Math.toDegrees(Math.atan2(target.y - origin.y, horizontal));
    }

    public static Vec3 unitPosition(Object unit) {
        if (worldSeatPosition != null) {
            try {
                Object value = worldSeatPosition.invoke(unit);
                if (value instanceof Vec3 vec) {
                    return vec;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        if (aimAtPosition != null) {
            try {
                Object value = aimAtPosition.invoke(unit, 0.0F);
                if (value instanceof Vec3 vec) {
                    return vec;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    public static List<Object> seatUnits(Object vehicle) {
        if (!resolve()) {
            return List.of();
        }
        try {
            Object seats = seatsField.get(vehicle);
            if (!(seats instanceof List<?> list)) {
                return List.of();
            }
            List<Object> units = new java.util.ArrayList<>();
            for (Object seat : list) {
                Object unit = partUnitField.get(seat);
                if (unit != null) {
                    units.add(unit);
                }
            }
            return units;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return List.of();
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return !broken;
        }
        resolved = true;
        try {
            Class<?> vehicleClass = Class.forName("org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
            seatsField = vehicleClass.getField("seats");
            partUnitField = Class.forName("org.ywzj.vehicle.entity.vehicle.AbstractVehicle$Seat")
                    .getField("partUnit");
            Class<?> unitClass = Class.forName("org.ywzj.vehicle.vehicle.part.WeaponUnit");
            ySelfRotField = unitClass.getField("ySelfRot");
            try {
                worldSeatPosition = unitClass.getMethod("worldSeatPosition");
            } catch (NoSuchMethodException ignored) {
                worldSeatPosition = null;
            }
            try {
                aimAtPosition = unitClass.getMethod("aimAtPosition", float.class);
            } catch (NoSuchMethodException ignored) {
                aimAtPosition = null;
            }
            try {
                worldRot = unitClass.getMethod("worldRot");
            } catch (NoSuchMethodException ignored) {
                worldRot = null;
            }
            getCurrentWeapon = unitClass.getMethod("getCurrentWeapon");
            Class<?> weaponClass = Class.forName("org.ywzj.vehicle.vehicle.weapon.AbstractVehicleWeapon");
            getData = weaponClass.getMethod("getData");
            Class<?> dataClass = Class.forName("org.ywzj.vehicle.custom.weapon.data.BaseVehicleWeaponData");
            try {
                getXRotMin = dataClass.getMethod("getXRotMin");
                getXRotMax = dataClass.getMethod("getXRotMax");
            } catch (NoSuchMethodException missing) {
                getXRotMin = null;
                getXRotMax = null;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            return false;
        }
    }
}
