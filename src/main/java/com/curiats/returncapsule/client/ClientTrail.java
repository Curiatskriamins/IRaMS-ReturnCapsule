package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.FallingCapsuleEntity;
import com.curiats.returncapsule.entity.SupplyCapsuleEntity;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

public final class ClientTrail {
    public static void onFallingTick(FallingCapsuleEntity entity) {
        if (!(entity.level() instanceof ClientLevel level)) {
            return;
        }
        if (entity instanceof SupplyCapsuleEntity) {
            supplyTrail(level, entity);
        } else if (entity instanceof TransportCapsuleEntity transport) {
            transportPhase(level, transport);
        }
    }

    private static void supplyTrail(ClientLevel level, FallingCapsuleEntity entity) {
        RandomSource random = level.random;
        double cx = entity.getX();
        double cy = entity.getY() + 1.0D;
        double cz = entity.getZ();

        emit(level, random, ParticleTypes.LARGE_SMOKE, 8, cx, cy, cz, 2.5D, 2.2D, 2.5D, -0.6D, 0.02D);
        emit(level, random, ParticleTypes.SMOKE, 8, cx, cy, cz, 2.0D, 2.0D, 2.0D, -0.4D, 0.015D);
        if (nearGround(level, entity)) {
            // Fire and touchdown smoke belong to the landing, not to the whole descent.
            emit(level, random, ParticleTypes.CAMPFIRE_COSY_SMOKE, 5, cx, cy, cz, 2.0D, 8.0D, 2.0D, -0.4D, 0.035D);
            emit(level, random, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 2, cx, cy, cz, 1.5D, 7.0D, 1.5D, 0.0D, 0.025D);
            emit(level, random, ParticleTypes.FLAME, 5, cx, cy, cz, 1.6D, 1.8D, 1.6D, -0.4D, 0.01D);
            if (entity.tickCount % 4 == 0) {
                emit(level, random, ParticleTypes.EXPLOSION, 1, cx, cy, cz, 1.2D, 1.2D, 1.2D, 0.0D, 0.0D);
            }
        }
    }

    private static final double ATMOSPHERE_TOP = 120_000.0D;

    private static final double ATMOSPHERE_BOTTOM = 10_800.0D;

    private static void transportPhase(ClientLevel level, TransportCapsuleEntity entity) {
        double remaining = entity.getY() - entity.landingY();
        if (remaining > ATMOSPHERE_TOP) {
            // Space: no particles at all.
            return;
        }
        if (remaining > ATMOSPHERE_BOTTOM) {
            plasmaRing(level, entity);
            return;
        }
        retroFlames(level, entity);
        landingBurn(level, level.random, entity);
    }

    /** A ring of flame and black smoke around the hull while it is actually inside the atmosphere. */
    private static void plasmaRing(ClientLevel level, TransportCapsuleEntity entity) {
        RandomSource random = level.random;
        double y = entity.getY() + 1.5D;
        float pitch = TransportAttitude.pitchDegrees(entity);
        double cosP = Math.cos(Math.toRadians(pitch));
        double sinP = Math.sin(Math.toRadians(pitch));
        double cosY = Math.cos(Math.toRadians(180.0D - entity.getYRot() + Config.MODEL_YAW_OFFSET.get().floatValue()));
        double sinY = Math.sin(Math.toRadians(180.0D - entity.getYRot() + Config.MODEL_YAW_OFFSET.get().floatValue()));
        for (int index = 0; index < 10; index++) {
            double angle = index * Math.PI * 2.0D / 10.0D + entity.tickCount * 0.05D;
            double localX = Math.cos(angle) * 2.2D;
            double localZ = Math.sin(angle) * 2.2D;
            double pitchedZ = localZ * cosP;
            double pitchedY = -localZ * sinP;
            double x = entity.getX() + localX * cosY + pitchedZ * sinY;
            double z = entity.getZ() - localX * sinY + pitchedZ * cosY;
            double ringY = y + pitchedY;
            level.addParticle(ParticleTypes.FLAME, true, x, ringY, z,
                    Math.cos(angle) * 0.05D, 0.02D, Math.sin(angle) * 0.05D);
            level.addParticle(ParticleTypes.LARGE_SMOKE, true, x, ringY - 0.4D, z,
                    Math.cos(angle) * 0.03D, -0.02D, Math.sin(angle) * 0.03D);
            if (random.nextFloat() < 0.3F) {
                level.addParticle(ParticleTypes.SMOKE, true, x, ringY - 0.8D, z, 0.0D, -0.04D, 0.0D);
            }
        }
    }

    /** Retro rockets burn from the atmosphere bottom all the way down, four nozzles. */
    private static void retroFlames(ClientLevel level, TransportCapsuleEntity entity) {
        RandomSource random = level.random;
        MannedCapsuleGeoModel.forEachThruster(entity, point -> {
            level.addParticle(ParticleTypes.FLAME, true, point.x, point.y, point.z,
                    random.nextDouble() * 0.04D - 0.02D, -1.2D, random.nextDouble() * 0.04D - 0.02D);
            if (random.nextFloat() < 0.25F) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, true, point.x, point.y, point.z,
                        0.02D, -0.4D, 0.02D);
            }
        });
    }

    /** Fire, explosion puffs and touchdown smoke only make sense in the last few blocks. */
    private static boolean nearGround(ClientLevel level, FallingCapsuleEntity entity) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
        return entity.getY() - groundY < 8.0D;
    }

    /** Retro rocket flames in the last three blocks, and the dust cloud at touchdown. */
    private static void landingBurn(ClientLevel level, RandomSource random, FallingCapsuleEntity entity) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
        double currentY = entity.getY();
        double gap = currentY - groundY;
        if (gap > 3.5D || gap < -2.0D) {
            return;
        }
        // Terminal velocity is close to four blocks per tick, so the last three blocks happen inside one tick: sample
        // the movement segment instead of emitting once per tick, otherwise the jet is a few disconnected puffs.
        double fromY = Math.max(entity.yOld, groundY + 3.0D);
        double step = 0.3D;
        if (fromY < currentY - 0.01D) {
            MannedCapsuleGeoModel.forEachThruster(entity, point -> {
                double localY = point.y - currentY;
                for (double y = fromY; y <= currentY + 1.0E-4D; y += step) {
                    emit(level, random, ParticleTypes.FLAME, 2, point.x, y + localY, point.z,
                            0.14D, 0.06D, 0.14D, 0.0D, -1.4D);
                }
            });
        } else {
            MannedCapsuleGeoModel.forEachThruster(entity, point -> emit(level, random, ParticleTypes.FLAME, 3,
                    point.x, point.y, point.z, 0.14D, 0.08D, 0.14D, 0.0D, -1.2D));
        }
        if (gap <= 1.0D) {
            // Touchdown: campfire smoke spreading out under the hull.
            double gy = groundY + 0.15D;
            emit(level, random, ParticleTypes.CAMPFIRE_COSY_SMOKE, 90, entity.getX(), gy, entity.getZ(),
                    4.6D, 0.6D, 4.6D, 0.0D, 0.05D);
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
