package com.curiats.returncapsule.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class TrueDot {
    private static final Map<UUID, Dot> DOTS = new HashMap<>();

    public static void apply(ServerLevel level, LivingEntity target, @Nullable Entity bomb,
                             @Nullable Entity owner, int ticks, float perTick) {
        DOTS.put(target.getUUID(), new Dot(level.dimension(), target.getUUID(), bomb, owner, ticks, perTick));
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Dot>> iterator = DOTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Dot dot = iterator.next().getValue();
            ServerLevel level = server.getLevel(dot.dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }
            Entity entity = level.getEntity(dot.target);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                iterator.remove();
                continue;
            }
            living.invulnerableTime = 0;
            living.hurt(TrueDamage.boom(level, dot.bomb, dot.owner), dot.perTick);
            dot.ticks--;
            if (dot.ticks <= 0) {
                iterator.remove();
            }
        }
    }

    private static final class Dot {
        private final ResourceKey<Level> dimension;
        private final UUID target;
        private final Entity bomb;
        private final Entity owner;
        private final float perTick;
        private int ticks;

        private Dot(ResourceKey<Level> dimension, UUID target, @Nullable Entity bomb,
                    @Nullable Entity owner, int ticks, float perTick) {
            this.dimension = dimension;
            this.target = target;
            this.bomb = bomb;
            this.owner = owner;
            this.ticks = ticks;
            this.perTick = perTick;
        }
    }

    private TrueDot() {
    }
}
