package com.curiats.returncapsule.entity.fall;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ShakeBurst {
    public static void around(ServerLevel level, double x, double y, double z,
                              double radius, int durationTicks, int amplifier) {
        double limit = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= limit) {
                player.addEffect(new MobEffectInstance(ModEffects.SHAKE.get(), durationTicks, amplifier,
                        false, false, false));
            }
        }
    }

    public static void forPassengers(Entity vehicle, int durationTicks, int amplifier) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                player.addEffect(new MobEffectInstance(ModEffects.SHAKE.get(), durationTicks, amplifier,
                        false, false, false));
            }
        }
    }

    public static void falling(ServerLevel level, Vec3 anchor, Vec3 capsule, double progress,
                               int maxAmplifier, int durationTicks) {
        int amplifier = Math.max(0, Math.min(maxAmplifier, (int) (progress * (maxAmplifier + 1))));
        around(level, anchor.x, anchor.y, anchor.z, Config.SHAKE_RADIUS.get(), durationTicks, amplifier);
    }

    private ShakeBurst() {
    }
}
