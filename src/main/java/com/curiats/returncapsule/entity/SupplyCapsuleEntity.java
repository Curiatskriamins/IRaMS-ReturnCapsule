package com.curiats.returncapsule.entity;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.compat.ShockManager;
import com.curiats.returncapsule.entity.fall.CapsuleParticles;
import com.curiats.returncapsule.entity.fall.CapsulePlacer;
import com.curiats.returncapsule.entity.fall.LandingSite;
import com.curiats.returncapsule.entity.fall.ShakeBurst;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.registry.ModEntities;
import com.curiats.returncapsule.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SupplyCapsuleEntity extends FallingCapsuleEntity {
    private static final DustParticleOptions MARKER_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.02F, 0.02F), 1.2F);
    private static final int FALL_SHAKE_AMPLIFIER = 3;
    private static final int FALL_SHAKE_DURATION = 10;

    public SupplyCapsuleEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public SupplyCapsuleEntity(Level level, BlockPos target, @Nullable Player owner, SummonTier tier) {
        this(ModEntities.SUPPLY_CAPSULE.get(), level);
        this.setTier(tier);
        this.beginFall(target, owner, Config.spawnHeight());
    }

    @Override
    protected double fallSpeed(double currentVelocity) {
        return Math.max(currentVelocity - Config.GRAVITY.get(), -Config.TERMINAL_VELOCITY.get());
    }

    @Override
    protected void emitFallParticles(ServerLevel server) {
        if (this.anchor == null) {
            return;
        }
        double x = this.anchor.getX() + 0.5D;
        double y = this.anchor.getY() + 0.15D;
        double z = this.anchor.getZ() + 0.5D;
        CapsuleParticles.nearby(server, ParticleTypes.SMOKE, x, y, z, 2, 0.28D, 0.04D, 0.28D, 0.004D);
        CapsuleParticles.nearby(server, MARKER_DUST, x, y, z, 2, 0.28D, 0.04D, 0.28D, 0.004D);
    }

    @Override
    protected void applyFallShake(ServerLevel server) {
        if (!Config.SCREEN_SHAKE.get() || this.anchor == null) {
            return;
        }
        double start = Config.SHAKE_START_DISTANCE.get();
        Vec3 anchorCenter = Vec3.atCenterOf(this.anchor);
        if (this.position().distanceToSqr(anchorCenter) > start * start) {
            return;
        }
        double travelled = (this.getY() - this.anchor.getY()) / Math.max(1.0D, this.spawnY() - this.anchor.getY());
        double progress = 1.0D - Mth.clamp(travelled, 0.0D, 1.0D);
        ShakeBurst.falling(server, anchorCenter, this.position(), progress,
                FALL_SHAKE_AMPLIFIER, FALL_SHAKE_DURATION);
    }

    @Override
    protected void onLanded(ServerLevel server) {
        if (this.impacted || this.anchor == null) {
            return;
        }
        this.impacted = true;

        this.anchor = LandingSite.findLanding(server, this.anchor);
        BlockPos landing = this.anchor;
        double x = landing.getX() + 0.5D;
        double y = landing.getY() + 1.0D;
        double z = landing.getZ() + 0.5D;

        server.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                Config.IMPACT_SOUND_VOLUME.get().floatValue(),
                Config.IMPACT_SOUND_PITCH.get().floatValue());
        // Slowed anvil by default; the bundled steel_impact pack swaps this event for metal.
        server.playSound(null, x, y, z, ModSounds.CAPSULE_IMPACT.get(), SoundSource.BLOCKS, 1.2F,
                Config.IMPACT_THUD_PITCH.get().floatValue());
        this.spawnImpactParticles(server, x, y, z);

        if (Config.SCREEN_SHAKE.get()) {
            ShakeBurst.around(server, x, y, z, Config.SHAKE_RADIUS.get(), 25, 0);
        }

        Entity owner = this.ownerUuid == null ? null : server.getEntity(this.ownerUuid);
        Set<UUID> excluded = new HashSet<>(2);
        if (!Config.DAMAGE_OWNER.get() && owner != null) {
            excluded.add(owner.getUUID());
        }
        ShockManager.impact(server, new Vec3(x, y, z), Config.IMPACT_HALF_SIZE.get(), owner, excluded);

        this.spawnExplosionCloud(server, x, y, z);
        LandingSite.scatterDrops(server, landing, this.random);
        if (Config.CLEAR_LANDING_AREA.get()) {
            LandingSite.clearArea(server, landing);
        }
        CapsulePlacer.withLootTable(server, landing, this.landingFacing,
                this.tier.lootTable(), this.tier);
        this.discard();
    }

    private void spawnImpactParticles(ServerLevel server, double x, double y, double z) {
        CapsuleParticles.broadcast(server, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 4, 1.6D, 0.6D, 1.6D, 0.0D);
        CapsuleParticles.broadcast(server, ParticleTypes.EXPLOSION, x, y, z, 30, 4.5D, 1.0D, 4.5D, 0.0D);
        CapsuleParticles.broadcast(server, ParticleTypes.LARGE_SMOKE, x, y, z, 160, 7.0D, 1.8D, 7.0D, 0.08D);
        CapsuleParticles.broadcast(server, ParticleTypes.SMOKE, x, y, z, 120, 6.0D, 1.5D, 6.0D, 0.05D);
        CapsuleParticles.broadcast(server, ParticleTypes.FLAME, x, y - 0.2D, z, 120, 5.0D, 1.0D, 5.0D, 0.09D);
        CapsuleParticles.broadcast(server, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 60, 6.0D, 1.4D, 6.0D, 0.03D);

        int ring = 64;
        for (int i = 0; i < ring; i++) {
            double angle = i * Math.PI * 2.0D / ring;
            double radius = 4.0D + this.random.nextDouble() * 7.0D;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            CapsuleParticles.broadcast(server, ParticleTypes.LARGE_SMOKE, px, y - 0.8D, pz,
                    1, 0.3D, 0.3D, 0.3D, 0.02D);
            if ((i & 1) == 0) {
                CapsuleParticles.broadcast(server, ParticleTypes.FLAME, px, y - 0.8D, pz,
                        1, 0.25D, 0.25D, 0.25D, 0.01D);
            }
        }
    }

    private void spawnExplosionCloud(ServerLevel server, double x, double y, double z) {
        CapsuleParticles.broadcast(server, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 4, 8.0D, 1.2D, 8.0D, 0.0D);
        CapsuleParticles.broadcast(server, ParticleTypes.LARGE_SMOKE, x, y, z, 100, 9.0D, 2.0D, 9.0D, 0.06D);
        CapsuleParticles.broadcast(server, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 50, 9.0D, 2.0D, 9.0D, 0.03D);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Keep writing the legacy key so worlds saved by an older build stay readable.
        if (this.anchor != null) {
            tag.putLong(TAG_LEGACY_TARGET, this.anchor.asLong());
        }
    }

    // Referenced from data driven code paths that predate the base class split.
    public boolean isImpacted() {
        return this.impacted;
    }
}
