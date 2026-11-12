package com.curiats.returncapsule.item;

import com.curiats.returncapsule.entity.fall.CapsuleBreaker;
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
    private static final List<Tracked> TRACKED = new ArrayList<>();

    public static void launch(TamableAnimal pet) {
        pet.setNoGravity(true);
        pet.setInvulnerable(true);
        pet.setDeltaMovement(Vec3.ZERO);
        pet.fallDistance = 0.0F;
        TRACKED.add(new Tracked(pet.getUUID(), pet.level().dimension(),
                pet.getX(), pet.getY(), pet.getZ()));
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
            iterator.remove();
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
        }
        TRACKED.clear();
    }

    private static final class Tracked {
        private final UUID id;
        private final ResourceKey<Level> dimension;
        private final double x;
        private final double y;
        private final double z;
        private int ticks = TICKS;

        private Tracked(UUID id, ResourceKey<Level> dimension, double x, double y, double z) {
            this.id = id;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private PetLaunch() {
    }
}
