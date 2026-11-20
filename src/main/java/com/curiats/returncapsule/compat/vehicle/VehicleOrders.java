package com.curiats.returncapsule.compat.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short lived movement orders handed from the gunner to the driver.
 *
 * <p>When the cannon has no line the gunner does not keep staring: it asks the hull to shift to a spot that does have
 * one. The order expires on its own so a bad request cannot freeze the driver forever.</p>
 */
public final class VehicleOrders {

    private record Order(Vec3 point, long until) {
    }

    private static final Map<Integer, Order> ORDERS = new ConcurrentHashMap<>();

    public static void reposition(Entity vehicle, Vec3 point, int ticks) {
        ORDERS.put(vehicle.getId(), new Order(point, vehicle.level().getGameTime() + ticks));
    }

    /** @return the point to drive to, or {@code null} when there is no live order */
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
