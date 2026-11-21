package com.curiats.returncapsule.compat.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleOrders {

    private record Order(Vec3 point, long until) {
    }

    private static final Map<Integer, Order> ORDERS = new ConcurrentHashMap<>();

    public static void reposition(Entity vehicle, Vec3 point, int ticks) {
        ORDERS.put(vehicle.getId(), new Order(point, vehicle.level().getGameTime() + ticks));
    }

    public static Vec3 take(Entity vehicle) {
        Order order = ORDERS.get(vehicle.getId());
        if (order == null) {
            return null;
        }
        if (vehicle.level().getGameTime() > order.until()) {
            ORDERS.remove(vehicle.getId());
            return null;
        }
        return order.point();
    }

    public static void clear(Entity vehicle) {
        ORDERS.remove(vehicle.getId());
    }

    private VehicleOrders() {
    }
}
