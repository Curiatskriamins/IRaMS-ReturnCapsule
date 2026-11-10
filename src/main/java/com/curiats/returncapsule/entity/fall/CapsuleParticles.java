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
