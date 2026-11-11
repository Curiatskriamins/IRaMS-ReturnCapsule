package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.BoomEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client-side exhaust trail of the cruise bomb, emitted every tick at the engine and pushed
 * backwards along the flight direction. It deliberately avoids the large white signal-smoke
 * particle and stays behind the tail, so it cannot swallow the flare or look detached from the
 * missile.
 */
public final class BoomClientTrail {
    private static final DustParticleOptions FLAME_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.42F, 0.08F), 1.8F);

    public static void onTick(BoomEntity entity) {
        if (!(entity.level() instanceof ClientLevel level)) {
            return;
        }
        RandomSource random = level.random;
        float scale = Config.BOOM_MODEL_SCALE.get().floatValue();
        Vec3 direction = entity.moveDirection();
        Vec3 tail = entity.position().subtract(direction.scale(1.55D * scale));

        emit(level, random, ParticleTypes.LARGE_SMOKE, 8, tail, direction, 0.45D, 0.7D, 0.45D, 0.0D, 0.09D);
        emit(level, random, ParticleTypes.CAMPFIRE_COSY_SMOKE, 4, tail, direction, 0.4D, 1.2D, 0.4D, 0.0D, 0.06D);
        emit(level, random, ParticleTypes.SMOKE, 8, tail, direction, 0.4D, 0.6D, 0.4D, 0.0D, 0.08D);
        emit(level, random, ParticleTypes.FLAME, 8, tail, direction, 0.35D, 0.5D, 0.35D, 0.0D, 0.12D);
        emit(level, random, FLAME_DUST, 4, tail, direction, 0.35D, 0.5D, 0.35D, 0.0D, 0.10D);
        if (entity.tickCount % 2 == 0) {
            emit(level, random, ParticleTypes.EXPLOSION, 1, tail, direction, 0.5D, 0.5D, 0.5D, 0.0D, 0.02D);
        }
    }

    private static void emit(ClientLevel level, RandomSource random, ParticleOptions particle,
                             int count, Vec3 tail, Vec3 direction,
                             double halfX, double spreadY, double halfZ,
                             double offsetY, double backwardSpeed) {
        int scaled = scaled(count);
        if (scaled <= 0) {
            return;
        }
        for (int i = 0; i < scaled; i++) {
            double vx = -direction.x * backwardSpeed + (random.nextDouble() - 0.5D) * 0.02D;
            double vy = -direction.y * backwardSpeed + (random.nextDouble() - 0.5D) * 0.02D;
            double vz = -direction.z * backwardSpeed + (random.nextDouble() - 0.5D) * 0.02D;
            level.addParticle(particle, true,
                    tail.x + random.nextDouble() * halfX * 2.0D - halfX,
                    tail.y + random.nextDouble() * spreadY + offsetY,
                    tail.z + random.nextDouble() * halfZ * 2.0D - halfZ,
                    vx, vy, vz);
        }
    }

    private static int scaled(int count) {
        double scale = Config.TRAIL_SCALE.get();
        if (scale <= 0.0D) {
            return 0;
        }
        return (int) Math.round(count * scale);
    }

    private BoomClientTrail() {
    }
}
