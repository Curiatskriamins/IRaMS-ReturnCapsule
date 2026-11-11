package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.FallingCapsuleEntity;
import com.curiats.returncapsule.entity.SupplyCapsuleEntity;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

 
public final class ClientTrail {
    private static final DustParticleOptions TRANSPORT_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 1.4F);

    public static void onFallingTick(FallingCapsuleEntity entity) {
        if (!(entity.level() instanceof ClientLevel level)) {
            return;
        }
        if (entity instanceof SupplyCapsuleEntity) {
            supplyTrail(level, entity);
        } else if (entity instanceof TransportCapsuleEntity) {
            transportTrail(level, entity);
        }
    }

    private static void supplyTrail(ClientLevel level, FallingCapsuleEntity entity) {
        RandomSource random = level.random;
        double cx = entity.getX();
        double cy = entity.getY() + 1.0D;
        double cz = entity.getZ();

        emit(level, random, ParticleTypes.CAMPFIRE_COSY_SMOKE, 5, cx, cy, cz, 2.0D, 8.0D, 2.0D, -0.4D, 0.035D);
        emit(level, random, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 2, cx, cy, cz, 1.5D, 7.0D, 1.5D, 0.0D, 0.025D);
        emit(level, random, ParticleTypes.LARGE_SMOKE, 8, cx, cy, cz, 2.5D, 2.2D, 2.5D, -0.6D, 0.02D);
        emit(level, random, ParticleTypes.SMOKE, 8, cx, cy, cz, 2.0D, 2.0D, 2.0D, -0.4D, 0.015D);
        emit(level, random, ParticleTypes.FLAME, 5, cx, cy, cz, 1.6D, 1.8D, 1.6D, -0.4D, 0.01D);
        if (entity.tickCount % 4 == 0) {
            emit(level, random, ParticleTypes.EXPLOSION, 1, cx, cy, cz, 1.2D, 1.2D, 1.2D, 0.0D, 0.0D);
        }
    }

    private static void transportTrail(ClientLevel level, FallingCapsuleEntity entity) {
        RandomSource random = level.random;
        double cx = entity.getX();
        double cy = entity.getY() + 1.0D;
        double cz = entity.getZ();

        emit(level, random, ParticleTypes.CAMPFIRE_COSY_SMOKE, 45, cx, cy, cz, 3.0D, 12.0D, 3.0D, -0.5D, 0.02D);
        emit(level, random, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 18, cx, cy, cz, 2.5D, 10.0D, 2.5D, 0.0D, 0.018D);
        emit(level, random, ParticleTypes.LARGE_SMOKE, 55, cx, cy, cz, 3.5D, 4.0D, 3.5D, -0.8D, 0.015D);
        emit(level, random, ParticleTypes.SMOKE, 35, cx, cy, cz, 3.0D, 4.0D, 3.0D, -0.4D, 0.01D);
        emit(level, random, ParticleTypes.FLAME, 45, cx, cy, cz, 2.25D, 3.0D, 2.25D, -0.5D, -0.01D);
        emit(level, random, TRANSPORT_DUST, 30, cx, cy, cz, 4.0D, 10.0D, 4.0D, -1.0D, 0.0D);
        emit(level, random, ParticleTypes.CLOUD, 15, cx, cy, cz, 2.5D, 3.0D, 2.5D, 0.0D, 0.0D);
        if (entity.tickCount % 3 == 0) {
            emit(level, random, ParticleTypes.EXPLOSION, 6, cx, cy, cz, 1.5D, 1.8D, 1.5D, 0.0D, 0.0D);
        }
    }

     
    private static void emit(ClientLevel level, RandomSource random, ParticleOptions particle, int count,
                             double cx, double cy, double cz,
                             double halfX, double spreadY, double halfZ,
                             double offsetY, double velocityY) {
        int scaled = scaled(count);
        if (scaled <= 0) {
            return;
        }
        for (int i = 0; i < scaled; i++) {
            level.addParticle(particle, true,
                    cx + random.nextDouble() * halfX * 2.0D - halfX,
                    cy + random.nextDouble() * spreadY + offsetY,
                    cz + random.nextDouble() * halfZ * 2.0D - halfZ,
                    0.0D, velocityY, 0.0D);
        }
    }

    private static int scaled(int count) {
        double scale = Config.TRAIL_SCALE.get();
        if (scale <= 0.0D) {
            return 0;
        }
        return (int) Math.round(count * scale);
    }

    private ClientTrail() {
    }
}
