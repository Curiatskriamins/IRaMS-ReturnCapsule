package com.curiats.returncapsule.item;

import com.curiats.returncapsule.entity.fall.CapsuleBreaker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class PetLaunch {
    private static final int TICKS = 200;
    private static final double HEIGHT = 1000.0D;
    private static final int HALF = TICKS / 2;
    private static final String KEY_ACTIVE = "returncapsule_pet_launch";
    private static final String KEY_X = "returncapsule_pet_x";
    private static final String KEY_Y = "returncapsule_pet_y";
    private static final String KEY_Z = "returncapsule_pet_z";
    private static final String KEY_TICKS = "returncapsule_pet_ticks";
    private static final List<Tracked> TRACKED = new ArrayList<>();

    public static void launch(TamableAnimal pet) {
        pet.setNoGravity(true);
        pet.setInvulnerable(true);
        pet.setDeltaMovement(Vec3.ZERO);
        pet.fallDistance = 0.0F;
        CompoundTag data = pet.getPersistentData();
        data.putBoolean(KEY_ACTIVE, true);
        data.putDouble(KEY_X, pet.getX());
        data.putDouble(KEY_Y, pet.getY());
        data.putDouble(KEY_Z, pet.getZ());
        data.putInt(KEY_TICKS, TICKS);
        TRACKED.add(new Tracked(pet.getUUID(), pet.level().dimension(),
                pet.getX(), pet.getY(), pet.getZ(), TICKS));
    }

    public static void tick(ServerLevel level) {
        Iterator<Tracked> iterator = TRACKED.iterator();
        while (iterator.hasNext()) {
            Tracked tracked = iterator.next();
            if (!tracked.dimension.equals(level.dimension())) {
                continue;
            }
            Entity entity = level.getEntity(tracked.id);
            if (entity == null) {
                iterator.remove();
                continue;
            }
            tracked.ticks--;
            if (entity instanceof TamableAnimal pet) {
                pet.getPersistentData().putInt(KEY_TICKS, tracked.ticks);
            }
            int elapsed = TICKS - tracked.ticks;
            double y = elapsed <= HALF
                    ? tracked.y + HEIGHT * (elapsed / (double) HALF)
                    : tracked.y + HEIGHT * ((TICKS - elapsed) / (double) HALF);
            entity.setPos(tracked.x, y, tracked.z);
            CapsuleBreaker.visualSmash(level, tracked.x, tracked.z, y,
                    entity.getBbHeight(), (int) Math.floor(tracked.y), entity);
            if (tracked.ticks > 0) {
                continue;
            }
            entity.setNoGravity(false);
            entity.teleportTo(tracked.x, tracked.y, tracked.z);
            entity.setInvulnerable(false);
            entity.fallDistance = 0.0F;
            if (entity instanceof TamableAnimal pet) {
                pet.getPersistentData().remove(KEY_ACTIVE);
                pet.getPersistentData().remove(KEY_X);
                pet.getPersistentData().remove(KEY_Y);
                pet.getPersistentData().remove(KEY_Z);
                pet.getPersistentData().remove(KEY_TICKS);
            }
            iterator.remove();
        }
    }

    public static void recover(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof TamableAnimal pet)) {
                    continue;
                }
                CompoundTag data = pet.getPersistentData();
                if (!data.getBoolean(KEY_ACTIVE) || isTracked(pet.getUUID())) {
                    continue;
                }
                int ticks = Math.max(1, data.getInt(KEY_TICKS));
                TRACKED.add(new Tracked(pet.getUUID(), level.dimension(),
                        data.getDouble(KEY_X), data.getDouble(KEY_Y), data.getDouble(KEY_Z), ticks));
                pet.setNoGravity(true);
                pet.setInvulnerable(true);
            }
        }
    }

    public static void releaseAll(MinecraftServer server) {
        for (Tracked tracked : TRACKED) {
            ServerLevel level = server.getLevel(tracked.dimension);
            if (level == null) {
                continue;
            }
            Entity entity = level.getEntity(tracked.id);
            if (entity == null) {
                continue;
            }
            entity.setNoGravity(false);
            entity.teleportTo(tracked.x, tracked.y, tracked.z);
            entity.setInvulnerable(false);
            entity.fallDistance = 0.0F;
            if (entity instanceof TamableAnimal pet) {
                pet.getPersistentData().remove(KEY_ACTIVE);
                pet.getPersistentData().remove(KEY_X);
                pet.getPersistentData().remove(KEY_Y);
                pet.getPersistentData().remove(KEY_Z);
                pet.getPersistentData().remove(KEY_TICKS);
            }
        }
        TRACKED.clear();
    }

    private static boolean isTracked(UUID id) {
        for (Tracked tracked : TRACKED) {
            if (tracked.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static final class Tracked {
        private final UUID id;
        private final ResourceKey<Level> dimension;
        private final double x;
        private final double y;
        private final double z;
        private int ticks;

        private Tracked(UUID id, ResourceKey<Level> dimension, double x, double y, double z, int ticks) {
            this.id = id;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticks = ticks;
        }
    }

    private PetLaunch() {
    }
}
