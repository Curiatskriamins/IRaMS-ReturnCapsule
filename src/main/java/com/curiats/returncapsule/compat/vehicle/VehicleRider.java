package com.curiats.returncapsule.compat.vehicle;

import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VehicleRider {

    private static final double SEARCH_RADIUS = 64.0D;

    private static Method changeSeat;
    private static Method passengerCapacity;
    private static Field seatsField;
    private static Field seatPassengerId;
    private static boolean resolved;
    private static boolean broken;

    private VehicleRider() {
    }

    public static void tick(NeutralHumanoid humanoid) {
        if (humanoid.level().isClientSide || humanoid.isDowned() || humanoid.tickCount % 20 != 0) {
            return;
        }
        if (!humanoid.isVehicleCombat()) {
            return;
        }
        Entity ridden = humanoid.getVehicle();
        if (ridden != null) {
            return;
        }
        Entity best = null;
        double bestDistance = SEARCH_RADIUS * SEARCH_RADIUS;
        for (Entity candidate : humanoid.level().getEntities(humanoid,
                humanoid.getBoundingBox().inflate(SEARCH_RADIUS))) {
            if (candidate == humanoid || candidate.getPassengers().contains(humanoid)) {
                continue;
            }
            if (!isVehicle(candidate)) {
                continue;
            }
            if (freeSeat(candidate) < 0) {
                continue;
            }
            double distance = humanoid.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best == null) {
            humanoid.setVehicleCombat(false);
            return;
        }
        if (humanoid.distanceToSqr(best) > 16.0D) {
            humanoid.requestMove(best, 1.3D, 2);
            return;
        }
        humanoid.getNavigation().stop();
        humanoid.startRiding(best, true);
    }

    public static boolean releaseDriver(Entity vehicle) {
        if (vehicle == null || !resolve(vehicle)) {
            return false;
        }
        Entity occupant = occupantOf(vehicle, 0);
        if (occupant instanceof NeutralHumanoid humanoid) {
            humanoid.stopRiding();
            humanoid.setVehicleCombat(false);
            humanoid.suppressReactions(60);
            return true;
        }
        return false;
    }

    public static void releaseAll(NeutralHumanoid humanoid) {
        if (humanoid.getVehicle() != null) {
            humanoid.stopRiding();
            humanoid.suppressReactions(60);
        }
        humanoid.setVehicleCombat(false);
    }

    private static boolean isVehicle(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.vehicle.Boat) {
            return true;
        }
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().equals("AbstractVehicle")) {
                return true;
            }
        }
        return false;
    }

    private static int freeSeat(Entity vehicle) {
        int capacity = capacity(vehicle);
        for (int index = 0; index < capacity; index++) {
            if (occupantOf(vehicle, index) == null) {
                return index;
            }
        }
        return -1;
    }

    private static Entity occupantOf(Entity vehicle, int index) {
        try {
            Object seats = seatsField.get(vehicle);
            if (!(seats instanceof java.util.List<?> list) || index < 0 || index >= list.size()) {
                return null;
            }
            Object seat = list.get(index);
            Object id = seatPassengerId.get(seat);
            if (!(id instanceof Integer passengerId) || passengerId < 0) {
                return null;
            }
            return vehicle.level().getEntity(passengerId);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static int capacity(Entity vehicle) {
        if (vehicle instanceof net.minecraft.world.entity.vehicle.Boat) {
            return 2;
        }
        resolve(vehicle);
        if (passengerCapacity != null) {
            try {
                Object value = passengerCapacity.invoke(vehicle);
                if (value instanceof Integer capacity && capacity > 0) {
                    return capacity;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return vehicle.getPassengers().size() + 1;
    }

    private static boolean resolve(Entity vehicle) {
        if (resolved) {
            return !broken;
        }
        resolved = true;
        try {
            Class<?> type = vehicle.getClass();
            while (type != null) {
                if (type.getSimpleName().equals("AbstractVehicle")) {
                    seatsField = type.getField("seats");
                    seatPassengerId = Class.forName("org.ywzj.vehicle.entity.vehicle.AbstractVehicle$Seat")
                            .getField("passengerId");
                    passengerCapacity = type.getMethod("passengerCapacity");
                    changeSeat = type.getMethod("changeSeat", net.minecraft.world.entity.LivingEntity.class, int.class);
                    return true;
                }
                type = type.getSuperclass();
            }
            broken = true;
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            return false;
        }
    }

}
