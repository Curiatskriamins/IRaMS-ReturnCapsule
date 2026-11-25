package com.curiats.returncapsule.util;

import com.curiats.returncapsule.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The protection field of the standard storage anchor while it sits anywhere in a player's inventory.
 *
 * <p>Horizontal 64 blocks, vertical 16 (8 up and 8 down, exclusive upper bound). One anchor is enough to project the
 * field; a full anchor also slows hostile projectiles.</p>
 *
 * <p>the slow is applied once against the motion the projectile had when it entered the field instead of
 * multiplying its current motion every pass, and projectiles that leave every field get their motion restored. The
 * damage compensation only runs while the projectile is still flagged as slowed and never for explosion AOE.</p>
 */
public final class AnchorField {

    public static final double RADIUS = 32.0D;

    public static final double HEIGHT = 8.0D;

    /** Marked on projectiles that the field slowed, so the damage can be restored on impact. */
    public static final String TAG_SLOWED = "returncapsule_anchor_slowed";

    private static final String TAG_FACTOR = "returncapsule_anchor_slow_factor";

    private static final String TAG_MOTION_X = "returncapsule_anchor_motion_x";

    private static final String TAG_MOTION_Y = "returncapsule_anchor_motion_y";

    private static final String TAG_MOTION_Z = "returncapsule_anchor_motion_z";

    private static final double SLOW_FACTOR = 0.1D;

    private static final double MIN_MOTION_SQR = 1.0E-6D;

    private static final double MAX_COMPENSATION = 100.0D;

    /** Projectiles currently slowed, so they can be released once they leave every field. */
    private static final Map<UUID, ResourceKey<Level>> SLOWED_PROJECTILES = new ConcurrentHashMap<>();

    private AnchorField() {
    }

    public static boolean held(ServerPlayer player) {
        return storedAnchors(player) > 0;
    }

    public static boolean full(ServerPlayer player) {
        return countStored(player) >= AnchorStorage.CAPACITY;
    }

    public static int storedAnchors(ServerPlayer player) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isAnchor(stack)) {
                found++;
            }
        }
        if (isAnchor(player.getOffhandItem())) {
            found++;
        }
        return found;
    }

    public static int countStored(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isAnchor(stack)) {
                total += AnchorStorage.count(stack);
            }
        }
        if (isAnchor(player.getOffhandItem())) {
            total += AnchorStorage.count(player.getOffhandItem());
        }
        return total;
    }

    private static boolean isAnchor(ItemStack stack) {
        return stack.is(ModItems.STANDARD_STORAGE_ANCHOR.get());
    }

    /** True when this holder covers this position. The bounds are 64 x 16 blocks, so the edge itself is outside. */
    private static boolean covers(ServerPlayer holder, Vec3 position) {
        return Math.abs(holder.getX() - position.x) < RADIUS
                && Math.abs(holder.getZ() - position.z) < RADIUS
                && Math.abs(holder.getY() - position.y) < HEIGHT;
    }

    /** True when any player carrying an anchor covers this position. */
    public static boolean covers(ServerLevel level, Vec3 position) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && held(player) && covers(player, position)) {
                return true;
            }
        }
        return false;
    }

    /** True when a player with a full anchor covers this position, so projectiles should crawl. */
    public static boolean slowsProjectiles(ServerLevel level, Vec3 position) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && full(player) && covers(player, position)) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideAny(java.util.List<ServerPlayer> anchors, Vec3 position) {
        for (ServerPlayer player : anchors) {
            if (covers(player, position)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called for every projectile each slow pass; only non player projectiles are slowed. The scale is applied to the
     * motion saved when the projectile entered the field, so gravity or drag cannot change the ratio between passes.
     */
    public static void slow(Projectile projectile, java.util.List<ServerPlayer> anchors) {
        if (!(projectile.level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = projectile.getOwner();
        if (owner instanceof Player) {
            return;
        }
        if (!insideAny(anchors, projectile.position())) {
            restore(projectile);
            return;
        }
        Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < MIN_MOTION_SQR) {
            return;
        }
        CompoundTag data = projectile.getPersistentData();
        if (!data.getBoolean(TAG_SLOWED)) {
            data.putBoolean(TAG_SLOWED, true);
            data.putDouble(TAG_FACTOR, SLOW_FACTOR);
            data.putDouble(TAG_MOTION_X, motion.x);
            data.putDouble(TAG_MOTION_Y, motion.y);
            data.putDouble(TAG_MOTION_Z, motion.z);
            SLOWED_PROJECTILES.put(projectile.getUUID(), level.dimension());
        }
        Vec3 original = savedMotion(data);
        if (original == null) {
            return;
        }
        projectile.setDeltaMovement(original.scale(data.getDouble(TAG_FACTOR)));
    }

    /** Puts a slowed projectile back on the path it would have taken without the field. */
    public static void restore(Projectile projectile) {
        CompoundTag data = projectile.getPersistentData();
        if (!data.getBoolean(TAG_SLOWED)) {
            return;
        }
        Vec3 original = savedMotion(data);
        if (original != null) {
            projectile.setDeltaMovement(original);
        }
        clearSlowTags(data);
        SLOWED_PROJECTILES.remove(projectile.getUUID());
    }

    /**
     * Releases projectiles that were slowed but are no longer covered by any full anchor (the holder walked away, the
     * anchor was dropped or consumed, or the projectile left the field). Called from the server tick.
     */
    public static void restoreLeftovers(MinecraftServer server) {
        if (SLOWED_PROJECTILES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, ResourceKey<Level>>> iterator = SLOWED_PROJECTILES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ResourceKey<Level>> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getValue());
            if (level == null) {
                iterator.remove();
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Projectile projectile) || projectile.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (!slowsProjectiles(level, projectile.position())) {
                restore(projectile);
                iterator.remove();
            }
        }
    }

    /**
     * Damage multiplier applied when a slowed projectile lands. Explosions are excluded: their damage source uses the
     * exploding projectile as the direct entity for every victim, so multiplying there would multiply the whole blast.
     */
    public static float damageMultiplier(Entity direct, DamageSource source) {
        if (direct == null || source == null) {
            return 1.0F;
        }
        CompoundTag data = direct.getPersistentData();
        if (!data.getBoolean(TAG_SLOWED)) {
            return 1.0F;
        }
        if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) {
            return 1.0F;
        }
        double factor = data.getDouble(TAG_FACTOR);
        if (factor <= 0.0D || factor >= 1.0D) {
            return 1.0F;
        }
        return (float) Math.min(1.0D / factor, MAX_COMPENSATION);
    }

    public static AABB area(ServerPlayer player) {
        return new AABB(player.getX() - RADIUS, player.getY() - HEIGHT, player.getZ() - RADIUS,
                player.getX() + RADIUS, player.getY() + HEIGHT, player.getZ() + RADIUS);
    }

    private static Vec3 savedMotion(CompoundTag data) {
        if (!data.contains(TAG_MOTION_X)) {
            return null;
        }
        return new Vec3(data.getDouble(TAG_MOTION_X), data.getDouble(TAG_MOTION_Y),
                data.getDouble(TAG_MOTION_Z));
    }

    private static void clearSlowTags(CompoundTag data) {
        data.remove(TAG_SLOWED);
        data.remove(TAG_FACTOR);
        data.remove(TAG_MOTION_X);
        data.remove(TAG_MOTION_Y);
        data.remove(TAG_MOTION_Z);
    }
}
