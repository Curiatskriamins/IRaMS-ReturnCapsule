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

/**
 * Decides whether a third party vehicle mod projectile should be allowed to force load chunks.
 *
 * <p>{@code org.ywzj.vehicle.util.EntityUtil.keepChunkLoaded} adds two {@code POST_TELEPORT} region tickets of radius
 * three (a 7x7 chunk area) per projectile, and every missile, rocket and artillery shell calls it as soon as its next
 * position leaves the entity ticking area. A dozen projectiles therefore drag hundreds of chunks, and the terrain along
 * the flight path has to be generated. Our mixin asks this class first and cancels the ticket for ammunition that does
 * not need it, so the round simply freezes in unloaded terrain until a player comes near.</p>
 *
 * <p>Two kinds of round are exempt by default: a missile that already holds a target entity (the PvP beyond visual
 * range case) and a preset long range strike (the default pack's DF style weapon). Both are configuration.</p>
 */
public final class VehicleAmmoGate {

    private static final String AMMO_PACKAGE = "org.ywzj.vehicle.entity.weapon";

    private static final Map<Class<?>, Access> ACCESS = new ConcurrentHashMap<>();

    /** Frozen rounds and the game time after which they should be removed. */
    private static final Map<UUID, Deadline> FROZEN = new ConcurrentHashMap<>();

    /** How long a record survives when the round cannot be reached because its chunk is not loaded. */
    private static final long GRACE_TICKS = 2400L;

    private record Deadline(ResourceKey<Level> dimension, long expiresAt) {
    }

    public static boolean shouldFreeze(Entity entity) {
        try {
            return decide(entity);
        } catch (RuntimeException exception) {
            // Config not baked yet or a reflection surprise: never break the vehicle mod over a performance tweak.
            return false;
        }
    }

    private static boolean decide(Entity entity) {
        if (entity == null || entity.level().isClientSide() || !Config.VEHICLE_AMMO_FREEZE.get()) {
            return false;
        }
        Class<?> type = entity.getClass();
        if (!type.getName().startsWith(AMMO_PACKAGE)) {
            // Vehicles and UAVs are not ammunition and keep the vehicle mod's behaviour.
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

    /**
     * Records when this round started freezing. A frozen round does not tick, so its own lifetime counter rarely
     * advances; this deadline is the backstop that {@link #reap} enforces later. Recording is one map insertion per
     * round, so the length of the deadline has no cost of its own.
     */
    private static void note(Entity entity) {
        int ttl = Config.VEHICLE_AMMO_TTL_TICKS.get();
        if (ttl <= 0) {
            return;
        }
        FROZEN.putIfAbsent(entity.getUUID(),
                new Deadline(entity.level().dimension(), entity.level().getGameTime() + ttl));
    }

    /**
     * Low frequency sweep: called every couple of hundred ticks, never per tick. Rounds whose chunk is loaded are
     * discarded straight away; rounds that are still in unloaded terrain keep their record for a grace period, because
     * they cannot be removed without loading the chunk they froze in.
     */
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
                // Cannot reach it without loading its chunk; forget it rather than grow this map forever.
                FROZEN.remove(entry.getKey());
            }
        }
    }

    public static void clear() {
        FROZEN.clear();
    }

    /**
     * Per class view on the vehicle mod's ammunition fields. Everything is reflective so the vehicle mod stays an
     * optional dependency.
     */
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
                    // keep walking up
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
