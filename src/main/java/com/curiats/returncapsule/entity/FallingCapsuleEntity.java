package com.curiats.returncapsule.entity;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.fall.CapsuleBreaker;
import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class FallingCapsuleEntity extends Entity {
    protected static final String TAG_ANCHOR = "Anchor";
    protected static final String TAG_LEGACY_TARGET = "Target";
    protected static final String TAG_LEGACY_MARKER = "MarkerPos";
    protected static final String TAG_OWNER = "Owner";
    protected static final String TAG_FACING = "LandingFacing";
    protected static final String TAG_TIER = "Tier";
    protected static final String TAG_FALL_AGE = "FallAge";
    protected static final String TAG_SPAWN_Y = "SpawnY";
    protected static final String TAG_IMPACTED = "Impacted";

    @Nullable
    protected BlockPos anchor;
    @Nullable
    protected UUID ownerUuid;
    protected Direction landingFacing = Direction.NORTH;
    protected SummonTier tier = SummonTier.BASIC;
    protected int fallAge;
    protected double spawnY;
    protected boolean impacted;
    private long lastBreakSoundTick = -100L;

    protected FallingCapsuleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    protected final void beginFall(BlockPos target, @Nullable Entity owner, double spawnHeight) {
        this.anchor = target.immutable();
        this.spawnY = target.getY() + spawnHeight;
        this.ownerUuid = owner == null ? null : owner.getUUID();
        this.setPos(target.getX() + 0.5D, this.spawnY, target.getZ() + 0.5D);
        if (owner != null) {
            this.landingFacing = facingFrom(target, owner);
            this.setYRot(this.landingFacing.toYRot());
        }
    }

    protected abstract double fallSpeed(double currentVelocity);

    protected abstract void onLanded(ServerLevel level);

    protected int floorY() {
        return this.anchor == null ? this.getBlockY() : this.anchor.getY();
    }

    protected void emitFallParticles(ServerLevel level) {
    }

    protected void applyFallShake(ServerLevel level) {
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            CapsuleClientHooks.onFallingTick(this);
            return;
        }
        if (this.impacted) {
            this.discard();
            return;
        }
        if (this.anchor == null) {
            this.discard();
            return;
        }

        ServerLevel server = (ServerLevel) this.level();
        this.fallAge++;
        this.smashPass(server);

        double beforeY = this.getY();
        double dy = this.fallSpeed(this.getDeltaMovement().y);
        this.setDeltaMovement(0.0D, dy, 0.0D);
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.getY() >= beforeY - 0.01D) {
            // The move was blocked; force the capsule downwards so it never stalls mid air.
            this.setPos(this.getX(), beforeY - Math.max(0.1D, -dy), this.getZ());
        }

        if (this.getY() <= this.anchor.getY()) {
            BlockPos landing = this.anchor;
            this.setPos(landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D);
            this.onLanded(server);
            return;
        }

        if (this.fallAge % 2 == 0) {
            this.emitFallParticles(server);
        }
        if (this.fallAge % 5 == 0) {
            this.applyFallShake(server);
        }
    }

    private void smashPass(ServerLevel level) {
        if (!Config.BREAK_BLOCKS.get()) {
            return;
        }
        int broken = CapsuleBreaker.smash(level, this.getX(), this.getZ(), this.getY(),
                this.getBbHeight(), this.floorY(), this);
        if (broken > 0 && level.getGameTime() - this.lastBreakSoundTick >= Config.SMASH_SOUND_INTERVAL.get()) {
            this.lastBreakSoundTick = level.getGameTime();
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS,
                    Config.SMASH_SOUND_VOLUME.get().floatValue(),
                    Config.SMASH_SOUND_PITCH.get().floatValue());
        }
    }

    protected static Direction facingFrom(BlockPos target, Entity viewer) {
        double dx = viewer.getX() - (target.getX() + 0.5D);
        double dz = viewer.getZ() - (target.getZ() + 0.5D);
        if (Math.abs(dx) < 1.0E-4D && Math.abs(dz) < 1.0E-4D) {
            return viewer.getDirection().getOpposite();
        }
        return Direction.getNearest(dx, 0.0D, dz);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(TAG_ANCHOR)) {
            this.anchor = BlockPos.of(tag.getLong(TAG_ANCHOR));
        } else if (tag.contains(TAG_LEGACY_TARGET)) {
            this.anchor = BlockPos.of(tag.getLong(TAG_LEGACY_TARGET));
        } else if (tag.contains(TAG_LEGACY_MARKER)) {
            this.anchor = BlockPos.of(tag.getLong(TAG_LEGACY_MARKER));
        }
        this.fallAge = tag.getInt(TAG_FALL_AGE);
        this.spawnY = tag.contains(TAG_SPAWN_Y)
                ? tag.getDouble(TAG_SPAWN_Y)
                : (this.anchor == null ? this.getY() : this.anchor.getY() + Config.spawnHeight());
        this.impacted = tag.getBoolean(TAG_IMPACTED);
        this.ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        Direction saved = tag.contains(TAG_FACING) ? Direction.byName(tag.getString(TAG_FACING)) : null;
        this.landingFacing = saved == null ? Direction.fromYRot(this.getYRot()) : saved;
        this.tier = SummonTier.byName(tag.getString(TAG_TIER));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.anchor != null) {
            tag.putLong(TAG_ANCHOR, this.anchor.asLong());
        }
        tag.putInt(TAG_FALL_AGE, this.fallAge);
        tag.putDouble(TAG_SPAWN_Y, this.spawnY);
        tag.putBoolean(TAG_IMPACTED, this.impacted);
        if (this.ownerUuid != null) {
            tag.putUUID(TAG_OWNER, this.ownerUuid);
        }
        tag.putString(TAG_FACING, this.landingFacing.getName());
        tag.putString(TAG_TIER, this.tier.id());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512.0D * 512.0D;
    }

    public int getFallAge() {
        return this.fallAge;
    }

    @Nullable
    public BlockPos getAnchor() {
        return this.anchor;
    }

    public Direction getLandingFacing() {
        return this.landingFacing;
    }

    public SummonTier getTier() {
        return this.tier;
    }

    public void setTier(SummonTier tier) {
        this.tier = tier == null ? SummonTier.BASIC : tier;
    }

    protected double spawnY() {
        return this.spawnY;
    }
}
