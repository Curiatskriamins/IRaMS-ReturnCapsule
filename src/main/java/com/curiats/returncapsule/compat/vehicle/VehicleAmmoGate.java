package com.curiats.returncapsule.compat.vehicle;

import com.curiats.returncapsule.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleAmmoGate {

    private static final String AMMO_PACKAGE = "org.ywzj.vehicle.entity.weapon";

    private static final Map<Class<?>, Access> ACCESS = new ConcurrentHashMap<>();

    private static final Map<UUID, Deadline> FROZEN = new ConcurrentHashMap<>();

    private static final long GRACE_TICKS = 2400L;

    private record Deadline(ResourceKey<Level> dimension, long expiresAt) {
    }

    public static boolean shouldFreeze(Entity entity) {
        try {
            return decide(entity);
        } catch (RuntimeException exception) {

            return false;
        }
    }

    private static boolean decide(Entity entity) {
        if (entity == null || entity.level().isClientSide() || !Config.VEHICLE_AMMO_FREEZE.get()) {
            return false;
        }
        Class<?> type = entity.getClass();
        if (!type.getName().startsWith(AMMO_PACKAGE)) {

            return false;
        }
        Access access = ACCESS.computeIfAbsent(type, Access::resolve);
        if (access.lockedTarget(entity) && Config.VEHICLE_AMMO_KEEP_HOMING.get()) {
            return false;
        }
        if (access.longRangeStrike(entity) && Config.VEHICLE_AMMO_KEEP_LONG_RANGE.get()) {
            return false;
        }
        note(entity);
        return true;
    }

    private static void note(Entity entity) {
        int ttl = Config.VEHICLE_AMMO_TTL_TICKS.get();
        if (ttl <= 0) {
            return;
        }
        FROZEN.putIfAbsent(entity.getUUID(),
                new Deadline(entity.level().dimension(), entity.level().getGameTime() + ttl));
    }

    public static void reap(MinecraftServer server) {
        if (FROZEN.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (Map.Entry<UUID, Deadline> entry : FROZEN.entrySet()) {
            Deadline deadline = entry.getValue();
            if (now < deadline.expiresAt()) {
                continue;
            }
            ServerLevel level = server.getLevel(deadline.dimension());
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity != null) {
                entity.discard();
                FROZEN.remove(entry.getKey());
            } else if (now > deadline.expiresAt() + GRACE_TICKS) {

                FROZEN.remove(entry.getKey());
            }
        }
    }

    public static void clear() {
        FROZEN.clear();
    }

    private record Access(Field targetEntity, Field presetTarget) {

        static Access resolve(Class<?> type) {
            return new Access(field(type, "targetEntity"), field(type, "presetTargetPos"));
        }

        private static Field field(Class<?> type, String name) {
            for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
                try {
                    Field field = cursor.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {

                } catch (RuntimeException exception) {
                    return null;
                }
            }
            return null;
        }

        boolean lockedTarget(Entity entity) {
            return read(this.targetEntity, entity) != null;
        }

        boolean longRangeStrike(Entity entity) {
            if (!(read(this.presetTarget, entity) instanceof Vec3 point)) {
                return false;
            }
            double blocks = Config.VEHICLE_AMMO_LONG_RANGE_BLOCKS.get();
            return point.distanceToSqr(entity.position()) > blocks * blocks;
        }

        private static Object read(Field field, Entity entity) {
            if (field == null) {
                return null;
            }
            try {
                return field.get(entity);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return null;
            }
        }
    }

    private VehicleAmmoGate() {
    }
}
