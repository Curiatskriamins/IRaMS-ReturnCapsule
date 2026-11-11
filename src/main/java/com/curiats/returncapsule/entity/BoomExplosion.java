package com.curiats.returncapsule.entity;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.compat.ShockManager;
import com.curiats.returncapsule.entity.fall.CapsuleParticles;
import com.curiats.returncapsule.entity.fall.ShakeBurst;
import com.curiats.returncapsule.registry.ModSounds;
import com.curiats.returncapsule.util.TrueDamage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Damage model of the cruise bomb.
 *
 * <p>The 30 ray occlusion test is the "30 rays reduce damage" rule from the design: every ray
 * that reaches the target counts, every ray that hits cover does not, and the resulting ratio
 * scales the damage between {@code minDamageMultiplier} and 1.</p>
 */
public final class BoomExplosion {
    public enum Mode {
        /** Reached anchor type II: 500 explosion damage plus the shock debuff. */
        ANCHOR,
        /** Touched terrain or an entity early: 500 true damage. */
        PREMATURE
    }

    public static void detonate(ServerLevel level, Vec3 center, @Nullable BoomEntity bomb,
                                @Nullable Entity owner, Mode mode) {
        double radius = Config.boomRadius();
        float power = Config.BOOM_POWER.get().floatValue();

        // Only the terrain-destroying mode uses the vanilla explosion. The default mode is drawn
        // by our own particles and damage, and plays a dedicated boom_impact event that references
        // the vanilla explosion sound. Because the event location is returncapsule:boom_impact,
        // ClientShake (which only reacts to GENERIC_EXPLODE) does not add camera shake.
        if (Config.BOOM_DESTROY_TERRAIN.get()) {
            level.explode(bomb, center.x, center.y, center.z, power, Level.ExplosionInteraction.BLOCK);
        } else {
            level.playSound(null, center.x, center.y, center.z, ModSounds.BOOM_IMPACT.get(),
                    SoundSource.BLOCKS, 4.0F, 0.8F);
        }

        spawnParticles(level, center, radius);

        // Same shake path as the return capsule landing: a SHAKE effect on nearby players, driven by
        // ClientShake. The boom_impact sound itself is a custom event, so it never triggers the
        // generic-explosion sound shake on top of this.
        if (Config.SCREEN_SHAKE.get()) {
            ShakeBurst.around(level, center.x, center.y, center.z, Config.SHAKE_RADIUS.get(), 25, 0);
        }

        double base = mode == Mode.ANCHOR ? Config.BOOM_DAMAGE.get() : Config.BOOM_TRUE_DAMAGE.get();
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!target.isAlive()) {
                continue;
            }
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            if (!Config.BOOM_HURT_OWNER.get() && owner != null && target.getUUID().equals(owner.getUUID())) {
                continue;
            }
            double factor = occlusionFactor(level, center, target, bomb);
            float damage = (float) (base * factor);
            if (damage <= 0.0F) {
                continue;
            }
            target.invulnerableTime = 0;
            DamageSource source = mode == Mode.ANCHOR
                    ? level.damageSources().explosion(bomb, owner)
                    : TrueDamage.boom(level, bomb, owner);
            target.hurt(source, damage);
            if (mode == Mode.ANCHOR && target.isAlive()) {
                ShockManager.applyShock(target, Config.BOOM_SHOCK_TICKS.get());
            }
        }
    }

    private static void spawnParticles(ServerLevel level, Vec3 center, double radius) {
        double x = center.x;
        double y = center.y;
        double z = center.z;
        CapsuleParticles.broadcast(level, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 5, 2.0D, 1.0D, 2.0D, 0.0D);
        CapsuleParticles.broadcast(level, ParticleTypes.EXPLOSION, x, y, z, 40, 4.0D, 1.5D, 4.0D, 0.0D);
        CapsuleParticles.broadcast(level, ParticleTypes.LARGE_SMOKE, x, y, z, 160,
                radius * 1.6D, 2.0D, radius * 1.6D, 0.08D);
        CapsuleParticles.broadcast(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 80,
                radius * 1.2D, 1.5D, radius * 1.2D, 0.03D);
        CapsuleParticles.broadcast(level, ParticleTypes.FLAME, x, y - 0.2D, z, 120,
                radius * 1.2D, 1.2D, radius * 1.2D, 0.07D);
    }

    private static double occlusionFactor(ServerLevel level, Vec3 center, LivingEntity target,
                                          @Nullable Entity source) {
        int rays = Math.max(1, Config.BOOM_RAYS.get());
        AABB box = target.getBoundingBox();
        int clear = 0;
        for (int i = 0; i < rays; i++) {
            double px = box.minX + (box.maxX - box.minX) * level.random.nextDouble();
            double py = box.minY + (box.maxY - box.minY) * level.random.nextDouble();
            double pz = box.minZ + (box.maxZ - box.minZ) * level.random.nextDouble();
            Vec3 point = new Vec3(px, py, pz);
            double distanceSqr = center.distanceToSqr(point);
            if (distanceSqr < 1.0E-6D) {
                clear++;
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(center, point,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
            if (hit.getType() == HitResult.Type.MISS
                    || hit.getLocation().distanceToSqr(center) >= distanceSqr - 0.01D) {
                clear++;
            }
        }
        double minimum = Config.BOOM_MIN_DAMAGE_MULTIPLIER.get();
        double ratio = clear / (double) rays;
        return minimum + (1.0D - minimum) * ratio;
    }

    private BoomExplosion() {
    }
}
