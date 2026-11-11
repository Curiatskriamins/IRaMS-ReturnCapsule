package com.curiats.returncapsule.entity.fall;

import com.curiats.returncapsule.Config;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class CapsuleParticles {

    public static void broadcast(ServerLevel level, ParticleOptions particle,
                                 double x, double y, double z, int count,
                                 double dx, double dy, double dz, double speed) {
        int scaled = scaled(count);
        if (scaled <= 0) {
            return;
        }
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(particle, true,
                x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, scaled);
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    public static void nearby(ServerLevel level, ParticleOptions particle,
                              double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed) {
        int scaled = scaled(count);
        if (scaled <= 0) {
            return;
        }
        level.sendParticles(particle, x, y, z, scaled, dx, dy, dz, speed);
    }

    /** Sends the particles only to players within {@code radius} blocks of the point. */
    public static void within(ServerLevel level, double radius, ParticleOptions particle,
                              double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed) {
        int scaled = scaled(count);
        if (scaled <= 0) {
            return;
        }
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(particle, true,
                x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, scaled);
        double limit = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= limit) {
                player.connection.send(packet);
            }
        }
    }

    private static int scaled(int count) {
        double scale = Config.PARTICLE_SCALE.get();
        if (scale <= 0.0D) {
            return 0;
        }
        return (int) Math.round(count * scale);
    }

    private CapsuleParticles() {
    }
}
