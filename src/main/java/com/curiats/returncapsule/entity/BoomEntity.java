package com.curiats.returncapsule.entity;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.fall.SuperBigCup;
import com.curiats.returncapsule.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

 
public class BoomEntity extends Entity {
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_LOCKED_ENTITY = "LockedEntity";
    private static final String TAG_LIFE = "Life";
    private static final String TAG_INCIDENCE = "Incidence";
    private static final String TAG_TARGET_X = "TargetX";
    private static final String TAG_TARGET_Y = "TargetY";
    private static final String TAG_TARGET_Z = "TargetZ";
    private static final String TAG_APPROACH_X = "ApproachX";
    private static final String TAG_APPROACH_Y = "ApproachY";
    private static final String TAG_APPROACH_Z = "ApproachZ";
    private static final double CONTACT_RADIUS = 1.0D;
    private static final double GRAZE_RADIUS = 2.5D;

    @Nullable
    private SuperBigCup controller;
    @Nullable
    private UUID ownerUuid;
    private Vec3 moveDir = new Vec3(0.0D, -1.0D, 0.0D);
    private int life;
    private boolean impacted;
    private boolean held;

    public void hold() {
        this.held = true;
    }

    public void release() {
        this.held = false;
        this.life = 0;
    }

    public BoomEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.faceDirection(this.moveDir);
    }

    public BoomEntity(Level level, SuperBigCup controller, @Nullable Player owner) {
        this(ModEntities.BOOM.get(), level);
        this.controller = controller;
        this.ownerUuid = owner == null ? null : owner.getUUID();
        Vec3 launch = controller.launchPoint();
        this.setPos(launch.x, launch.y, launch.z);
        this.faceDirection(controller.targetPoint().subtract(launch));
        this.setOldPosAndRot();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            
            
            BoomClientHooks.onTick(this);
            return;
        }
        if (this.impacted) {
            this.discard();
            return;
        }
        if (this.held) {
            return;
        }
        if (!(this.level() instanceof ServerLevel server) || this.controller == null) {
            this.discard();
            return;
        }
        if (++this.life > Config.BOOM_MAX_LIFETIME_TICKS.get()) {
            this.detonate(server, BoomExplosion.Mode.PREMATURE);
            return;
        }

        Vec3 from = this.position();
        this.controller.update(from);
        if (this.life % 2 == 0) {
            this.controller.markerParticles();
        }
        Vec3 target = this.controller.targetPoint();
        boolean locked = this.controller.lockedEntity() != null;
        Vec3 aim = locked ? this.controller.aimPoint()
                : from.add(this.moveDir.scale(100.0D));

        Vec3 desired = aim.subtract(from);
        if (desired.lengthSqr() < 1.0E-6D) {
            desired = this.moveDir;
        }
        Vec3 dir;
        if (locked && this.life % 2 == 0) {
            dir = this.controller.steer(from, desired.normalize(), this);
            this.faceDirection(dir);
        } else {
            dir = this.moveDir;
        }

        double speed = Config.TERMINAL_VELOCITY.get();
        Vec3 next = from.add(dir.scale(speed));
        this.setPos(next.x, next.y, next.z);

        
        if (locked && segmentDistanceSqr(from, next, target) <= CONTACT_RADIUS * CONTACT_RADIUS) {
            this.setPos(target.x, target.y, target.z);
            this.detonate(server, BoomExplosion.Mode.ANCHOR);
            return;
        }

        BlockHitResult hit = server.clip(new ClipContext(from, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            if (hit.getLocation().distanceToSqr(target) <= GRAZE_RADIUS * GRAZE_RADIUS) {
                
                this.setPos(target.x, target.y, target.z);
                this.detonate(server, BoomExplosion.Mode.ANCHOR);
                return;
            }
            this.setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
            this.detonate(server, BoomExplosion.Mode.PREMATURE);
            return;
        }

        AABB sweep = new AABB(from, next).inflate(0.5D);
        for (Entity entity : server.getEntities(this, sweep, this::isBombTarget)) {
            if (this.controller.isLockedEntity(entity.getUUID())) {
                this.setPos(target.x, target.y, target.z);
                this.detonate(server, BoomExplosion.Mode.ANCHOR);
            } else {
                this.detonate(server, BoomExplosion.Mode.PREMATURE);
            }
            return;
        }
    }

    private boolean isBombTarget(Entity entity) {
        if (entity == this || !(entity instanceof LivingEntity) || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (this.controller != null && this.controller.isLockedEntity(entity.getUUID())) {
            return true;
        }
        return Config.BOOM_HURT_OWNER.get() || this.ownerUuid == null
                || !this.ownerUuid.equals(entity.getUUID());
    }

    private void detonate(ServerLevel server, BoomExplosion.Mode mode) {
        if (this.impacted) {
            return;
        }
        this.impacted = true;
        Vec3 center = mode == BoomExplosion.Mode.ANCHOR
                ? this.controller == null ? this.position() : this.controller.targetPoint()
                : this.position();
        BoomExplosion.detonate(server, center, this, this.owner(), mode);
        this.discard();
    }

    @Nullable
    private Entity owner() {
        if (this.ownerUuid == null || !(this.level() instanceof ServerLevel server)) {
            return null;
        }
        return server.getEntity(this.ownerUuid);
    }

    private void faceDirection(Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        this.moveDir = direction.normalize();
        double horizontal = Math.sqrt(this.moveDir.x * this.moveDir.x + this.moveDir.z * this.moveDir.z);
        this.setYRot((float) Math.toDegrees(Math.atan2(this.moveDir.x, this.moveDir.z)));
        this.setXRot((float) Math.toDegrees(Math.atan2(-this.moveDir.y, horizontal)));
    }

    private static double segmentDistanceSqr(Vec3 start, Vec3 end, Vec3 point) {
        Vec3 segment = end.subtract(start);
        double length = segment.lengthSqr();
        if (length < 1.0E-8D) {
            return start.distanceToSqr(point);
        }
        double t = Mth.clamp(point.subtract(start).dot(segment) / length, 0.0D, 1.0D);
        return start.add(segment.scale(t)).distanceToSqr(point);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.life = tag.getInt(TAG_LIFE);
        this.held = tag.getBoolean("Held");
        this.impacted = tag.getBoolean("Impacted");
        this.ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        Vec3 fallback = new Vec3(tag.getDouble(TAG_TARGET_X), tag.getDouble(TAG_TARGET_Y),
                tag.getDouble(TAG_TARGET_Z));
        Vec3 approach = new Vec3(tag.getDouble(TAG_APPROACH_X), tag.getDouble(TAG_APPROACH_Y),
                tag.getDouble(TAG_APPROACH_Z));
        if (this.level() instanceof ServerLevel server) {
            UUID locked = tag.hasUUID(TAG_LOCKED_ENTITY) ? tag.getUUID(TAG_LOCKED_ENTITY) : null;
            this.controller = SuperBigCup.restore(server, locked, fallback, approach,
                    tag.getDouble(TAG_INCIDENCE));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(TAG_LIFE, this.life);
        tag.putBoolean("Held", this.held);
        tag.putBoolean("Impacted", this.impacted);
        if (this.ownerUuid != null) {
            tag.putUUID(TAG_OWNER, this.ownerUuid);
        }
        if (this.controller == null) {
            return;
        }
        if (this.controller.lockedEntity() != null) {
            tag.putUUID(TAG_LOCKED_ENTITY, this.controller.lockedEntity());
        }
        Vec3 target = this.controller.targetPoint();
        tag.putDouble(TAG_TARGET_X, target.x);
        tag.putDouble(TAG_TARGET_Y, target.y);
        tag.putDouble(TAG_TARGET_Z, target.z);
        Vec3 approach = this.controller.launchPoint().subtract(target);
        tag.putDouble(TAG_APPROACH_X, approach.x);
        tag.putDouble(TAG_APPROACH_Y, approach.y);
        tag.putDouble(TAG_APPROACH_Z, approach.z);
        tag.putDouble(TAG_INCIDENCE, this.controller.incidenceDegrees());
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

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        
        
        
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    public Vec3 moveDirection() {
        return this.moveDir;
    }

    public int getLife() {
        return this.life;
    }

    public boolean isImpacted() {
        return this.impacted;
    }
}
