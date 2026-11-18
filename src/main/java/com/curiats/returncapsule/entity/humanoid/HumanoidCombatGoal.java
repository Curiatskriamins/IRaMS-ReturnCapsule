package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.compat.tacz.TaczRifles;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

@SuppressWarnings({"this-escape"})
public class HumanoidCombatGoal extends Goal {

    private static final double STANCE_HYSTERESIS = 1.5D;

    private static final double MIN_GUN_RANGE = 4.0D;
    private static final double APPROACH_SPEED = 1.0D;

    private static final double DIRECT_STEER_RANGE = 14.0D;

    private static final double NAVIGATE_RANGE = 32.0D;

    private static final double MIN_APPROACH_RANGE = 10.0D;

    private static final int AIM_SETTLE_TICKS = 2;

    private static final int SIGHT_CACHE_TICKS = 5;

    private static final int AIM_SWITCH_TICKS = 20;

    private static final int STEP_BACK_TICKS = 8;

    private static final float STEP_BACK_SPEED = 0.6F;

    private static final float TURN_PER_TICK = 25.0F;

    private static final double ATTACK_CONE_COS = Math.cos(Math.toRadians(75.0D));

    private static final float RETREAT_SPEED_FACTOR = 0.7F;

    private static final int REPATH_INTERVAL = 10;

    private static final int STALL_TIMEOUT = 200;

    private static final int SIGHT_MEMORY = 40;

    private static final int FLANK_AFTER = 80;

    private static final int MAX_FLANKS = 2;
    private static final double BASE_ATTACK_SPEED = 4.0D;
    private static final double TICKS_PER_SECOND = 20.0D;

    private final NeutralHumanoid mob;
    private int attackCooldown;

    private int repathCooldown;

    private int stepBackTicks;

    private int targetId = -1;
    private float lastTargetHealth = -1.0F;
    private int stalledTicks;

    private int noSightTicks;

    private int flanksUsed;

    private int flankCooldown;

    private boolean flanking;

    private int aimTicks;

    private AimMode aimMode = AimMode.HEAD;

    private int repositionAttempts;

    private enum AimMode { HEAD, BODY }

    private int sightTargetId = -1;

    private int sightTicks;

    private boolean sightResult;

    private int repositionTicks;

    private int lastSweepParticle = -100;

    private int fallbackTicks;

    private int fallbackCooldown;

    private static final int FALLBACK_TICKS = 40;
    private static final int FALLBACK_COOLDOWN = 120;

    private static final int SWEEP_PARTICLE_INTERVAL = 10;

    public HumanoidCombatGoal(NeutralHumanoid mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isDowned() || this.mob.isPassenger()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive() || !this.mob.canAttack(target)) {
            return false;
        }
        double radius = this.mob.engagementRadiusFor(target) * HumanoidCombatRules.LENIENCE;
        return this.mob.distanceToSqr(target) <= radius * radius;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.mob.setAggressive(false);
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        boolean visible = this.visibleTo(target);
        if (visible) {
            this.noSightTicks = 0;
            this.flanksUsed = 0;
            this.flanking = false;
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        } else {
            this.noSightTicks++;
        }
        if (this.abandonIfStalled(target)) {
            return;
        }
        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }
        double distance = Math.sqrt(this.mob.distanceToSqr(target));

        boolean gun = this.mob.shouldHoldGun(distance, STANCE_HYSTERESIS);
        this.mob.useStance(gun ? NeutralHumanoid.Stance.RANGED : NeutralHumanoid.Stance.MELEE);

        if (!visible) {
            if (HumanoidGrenadier.tryThrow(this.mob, target, true)) {
                return;
            }
            if (this.noSightTicks > FLANK_AFTER) {
                if (this.flanksUsed >= MAX_FLANKS) {
                    this.flanksUsed = 0;
                    this.noSightTicks = 0;
                    this.mob.setTarget(null);
                    return;
                }
                if (this.flankCooldown <= 0) {
                    this.flanksUsed++;
                    this.flankCooldown = 60;
                    this.flanking = this.startFlank(target);
                }
            }
            if (this.flankCooldown > 0) {
                this.flankCooldown--;
            }
            if (this.flanking) {
                this.mob.setSprinting(true);
                return;
            }
            if (this.noSightTicks > SIGHT_MEMORY) {
                return;
            }
        }
        if (this.openGroundFallback(target)) {
            return;
        }
        if (this.mob.isRangedStance()) {
            this.mob.chooseGunFor(distance);
            this.holdRange(target, distance);
            HumanoidGrenadier.tryThrow(this.mob, target, false);
            if (!HumanoidSight.canSee(this.mob.level(), this.mob, target)) {
                this.attackCooldown = 5;
                return;
            }
        } else {
            this.turnToward(target);
            this.meleeMovement(target, distance);
            this.mob.setSprinting(true);
        }

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            return;
        }
        this.attack(target, distance);
    }

    private boolean abandonIfStalled(LivingEntity target) {
        if (target.getId() != this.targetId) {
            this.targetId = target.getId();
            this.lastTargetHealth = target.getHealth();
            this.stalledTicks = 0;
            return false;
        }
        float health = target.getHealth();
        if (health < this.lastTargetHealth - 1.0E-4F) {
            this.lastTargetHealth = health;
            this.stalledTicks = 0;
            this.aimMode = AimMode.HEAD;
            this.repositionAttempts = 0;
            return false;
        }
        this.stalledTicks++;
        if (this.stalledTicks == AIM_SWITCH_TICKS && this.aimMode == AimMode.HEAD) {
            this.aimMode = AimMode.BODY;
        }
        if (this.stalledTicks <= STALL_TIMEOUT) {
            return false;
        }
        this.stalledTicks = 0;
        if (this.repositionAttempts < 2) {
            this.repositionAttempts++;
            this.aimMode = AimMode.HEAD;
            this.repositionTicks = REPOSITION_TICKS;
            this.runAwayFrom(target);
            if (this.startFlank(target)) {
                this.flanking = true;
            }
            return false;
        }
        this.repositionAttempts = 0;
        this.aimMode = AimMode.HEAD;
        this.targetId = -1;
        this.mob.setTarget(null);
        return true;
    }

    private boolean openGroundFallback(LivingEntity target) {
        if (this.fallbackTicks > 0) {
            this.fallbackTicks--;
            return this.fallbackTicks > 0;
        }
        if (this.mob.hurtTime <= 0 || this.fallbackCooldown > 0) {
            return false;
        }
        if (this.fallbackCooldown > -200) {
            this.fallbackCooldown--;
        }
        if (this.isOpenGround(this.mob.blockPosition())) {
            this.fallbackCooldown = FALLBACK_COOLDOWN;
            return false;
        }
        net.minecraft.core.BlockPos spot = this.findOpenGround();
        if (spot == null) {
            this.fallbackCooldown = FALLBACK_COOLDOWN;
            return false;
        }
        this.mob.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.3D);
        this.fallbackTicks = FALLBACK_TICKS;
        this.fallbackCooldown = FALLBACK_COOLDOWN;
        return true;
    }

    private boolean isOpenGround(net.minecraft.core.BlockPos origin) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                net.minecraft.core.BlockPos floor = origin.offset(dx, -1, dz);
                if (this.mob.level().getBlockState(floor).getCollisionShape(this.mob.level(), floor).isEmpty()) {
                    return false;
                }
                net.minecraft.core.BlockPos head = origin.offset(dx, 0, dz);
                if (!this.mob.level().getBlockState(head).getCollisionShape(this.mob.level(), head).isEmpty()) {
                    return false;
                }
                net.minecraft.core.BlockPos above = origin.offset(dx, 1, dz);
                if (!this.mob.level().getBlockState(above).getCollisionShape(this.mob.level(), above).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private net.minecraft.core.BlockPos findOpenGround() {
        net.minecraft.core.BlockPos origin = this.mob.blockPosition();
        for (int radius = 2; radius <= 12; radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    net.minecraft.core.BlockPos candidate = origin.offset(dx, 0, dz);
                    if (this.isOpenGround(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static final double CHASE_DROP_LIMIT = 5.0D;

    private void holdRange(LivingEntity target, double distance) {
        if (distance < MIN_GUN_RANGE) {

            this.faceTarget(target);
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().strafe(-1.0F, 0.0F);
            this.mob.setSpeed(RETREAT_SPEED_FACTOR);
        } else if (distance > NAVIGATE_RANGE) {
            this.faceTarget(target);
            this.mob.getNavigation().stop();
        } else if (distance > MIN_APPROACH_RANGE) {
            if (!this.steerDirectly(target, APPROACH_SPEED)) {
                this.navigateTo(target, APPROACH_SPEED);
            }
        } else if (this.repositionTicks > 0) {
            this.repositionTicks--;
            this.runAwayFrom(target);
        } else if (distance < REPOSITION_DISTANCE && this.mob.getRandom().nextInt(REPOSITION_CHANCE) == 0) {

            this.repositionTicks = REPOSITION_TICKS;
            this.runAwayFrom(target);
        } else {

            if (distance < RETREAT_DISTANCE) {
                this.faceTarget(target);
                this.mob.getNavigation().stop();
                this.mob.getMoveControl().strafe(-0.6F, 0.0F);
                this.mob.setSpeed(0.55F);
                if (this.mob.horizontalCollision && this.mob.onGround()) {
                    this.mob.getJumpControl().jump();
                } else if (target.getY() - this.mob.getY() > 1.0D && this.mob.onGround()) {
                    this.mob.getJumpControl().jump();
                }
            } else {
                this.faceTarget(target);
                this.mob.getNavigation().stop();
            }
        }
    }

    private void runAwayFrom(LivingEntity target) {
        double dx = this.mob.getX() - target.getX();
        double dz = this.mob.getZ() - target.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }
        double x = this.mob.getX() + dx / length * REPOSITION_STEP;
        double z = this.mob.getZ() + dz / length * REPOSITION_STEP;
        this.mob.getNavigation().moveTo(x, this.mob.getY(), z, 1.4D);
        this.mob.setSpeed(1.0F);
    }

    private static final double REPOSITION_DISTANCE = 12.0D;

    private static final double RETREAT_DISTANCE = 7.0D;

    private static final double REPOSITION_STEP = 8.0D;

    private static final int REPOSITION_TICKS = 25;

    private static final int REPOSITION_CHANCE = 90;

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        this.mob.setYRot(yaw);
        this.mob.setYHeadRot(yaw);
        this.mob.yBodyRot = yaw;
    }

    private void meleeMovement(LivingEntity target, double distance) {
        if (this.stepBackTicks > 0) {
            this.stepBackTicks--;
            this.faceTarget(target);
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().strafe(-1.0F, 0.0F);
            this.mob.setSpeed(STEP_BACK_SPEED);
            return;
        }
        this.closeIn(target, distance);
    }

    private void closeIn(LivingEntity target, double distance) {
        if (this.steerDirectly(target, APPROACH_SPEED)) {
            return;
        }
        if (distance > HumanoidCombatRules.MELEE_REACH) {
            this.navigateTo(target, APPROACH_SPEED);
        } else {
            this.mob.getNavigation().stop();
        }
    }

    private boolean shouldRepath() {
        if (this.mob.getNavigation().isDone() || this.repathCooldown <= 0) {
            this.repathCooldown = REPATH_INTERVAL;
            return true;
        }
        return false;
    }

    private boolean steerDirectly(LivingEntity target, double speed) {
        double distance = Math.sqrt(this.mob.distanceToSqr(target));
        if (distance > DIRECT_STEER_RANGE || !HumanoidSight.canSee(this.mob.level(), this.mob, target)) {
            return false;
        }
        this.mob.getNavigation().stop();
        this.mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
        return true;
    }

    private void navigateTo(LivingEntity target, double speed) {
        if (this.shouldRepath()) {
            this.mob.requestMove(target, speed, 3);
        }
    }

    private void attack(LivingEntity target, double distance) {
        if (this.mob.isRangedStance()) {
            boolean clear = this.mob.cluster() != null && this.mob.cluster().contains(target)
                    ? HumanoidSight.canSee(this.mob.level(), this.mob, this.mob.getEyePosition(),
                            this.mob.aimPointFor(target))
                    : HumanoidSight.canSee(this.mob.level(), this.mob, target);
            if (!clear) {
                return;
            }
            boolean precision = this.mob.precisionWeapon();
            boolean aligned;
            if (precision) {
                aligned = this.mob.aimAt(target);
            } else {
                net.minecraft.world.phys.Vec3 point = this.mob.cluster() != null
                        ? this.mob.aimPointFor(target)
                        : this.aimMode == AimMode.HEAD
                                ? new net.minecraft.world.phys.Vec3(target.getX(), target.getEyeY(), target.getZ())
                                : target.getBoundingBox().getCenter();
                aligned = this.mob.aimAtPoint(point, this.mob.cluster() != null ? 12.0F : 7.0F);
            }
            if (!aligned) {
                this.aimTicks = 0;
                return;
            }
            if (++this.aimTicks < (precision ? AIM_SETTLE_TICKS : 1)) {
                return;
            }
            this.aimTicks = 0;
            this.attackCooldown = 0;
            this.mob.fireGun(target);
            return;
        }
        if (distance > HumanoidCombatRules.MELEE_REACH) {
            return;
        }

        if (!this.facing(target)) {
            return;
        }
        this.attackCooldown = this.meleeInterval();
        this.stepBackTicks = STEP_BACK_TICKS;
        this.mob.setAggressive(true);
        this.mob.markAttack();
        this.mob.swing(InteractionHand.MAIN_HAND);
        this.mob.doHurtTarget(target);
        this.sweep(target);
    }

    private void sweep(LivingEntity target) {
        if (!this.mob.wieldsBlade()) {
            return;
        }
        if (this.mob.tickCount - this.lastSweepParticle < SWEEP_PARTICLE_INTERVAL) {
            return;
        }
        this.lastSweepParticle = this.mob.tickCount;
        if (this.mob.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + 0.8D, target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        float cleave = (float) (this.mob.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.5D);
        net.minecraft.world.phys.AABB area = net.minecraft.world.phys.AABB.ofSize(
                target.position().add(0.0D, 1.0D, 0.0D), 3.0D, 2.0D, 3.0D);
        for (LivingEntity nearby : this.mob.level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != this.mob && candidate != target && candidate.isAlive()
                        && !(candidate instanceof net.minecraft.world.entity.player.Player))) {
            nearby.invulnerableTime = 0;
            nearby.hurt(this.mob.damageSources().mobAttack(this.mob), cleave);
        }
    }

    private boolean visibleTo(LivingEntity target) {
        int id = target.getId();
        if (id != this.sightTargetId || this.sightTicks <= 0) {
            this.sightTargetId = id;
            this.sightTicks = SIGHT_CACHE_TICKS;
            if (this.mob.cluster() != null && this.mob.cluster().contains(target)) {
                this.sightResult = HumanoidSight.canSee(this.mob.level(), this.mob,
                        this.mob.getEyePosition(), this.mob.aimPointFor(target));
            } else {
                this.sightResult = HumanoidSight.canSee(this.mob.level(), this.mob, target);
            }
        }
        this.sightTicks--;
        return this.sightResult;
    }


    private boolean startFlank(LivingEntity target) {
        net.minecraft.core.BlockPos targetPos = target.blockPosition();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = this.mob.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 6.0D + this.mob.getRandom().nextDouble() * 3.0D;
            net.minecraft.core.BlockPos spot = net.minecraft.core.BlockPos.containing(
                    targetPos.getX() + Math.cos(angle) * radius, this.mob.getY(),
                    targetPos.getZ() + Math.sin(angle) * radius);
            if (!HumanoidTraversal.canStandOn(this.mob.level(), spot)) {
                continue;
            }
            if (this.mob.onHighPlatform() && Math.abs(spot.getY() - this.mob.platformY()) > 1) {
                continue;
            }
            this.mob.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.3D);
            return true;
        }
        return false;
    }

    private void turnToward(LivingEntity target) {
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float desired = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float current = this.mob.getYRot();
        float delta = Mth.wrapDegrees(desired - current);
        float step = Mth.clamp(delta, -TURN_PER_TICK, TURN_PER_TICK);
        float yaw = current + step;
        this.mob.setYRot(yaw);
        this.mob.setYHeadRot(yaw);
        this.mob.yBodyRot = yaw;
    }

    private boolean facing(LivingEntity target) {
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        double lengthSqr = dx * dx + dz * dz;
        if (lengthSqr < 1.0E-4D) {
            return true;
        }
        double yaw = Math.toRadians(this.mob.getYRot());
        double dot = (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / Math.sqrt(lengthSqr);
        return dot >= ATTACK_CONE_COS;
    }

    private int gunInterval() {
        int rpm = TaczRifles.rpm(this.mob.getMainHandItem());
        if (rpm <= 0) {

            return 1;
        }
        return Math.max(1, Math.min(Math.round(1200.0F / rpm), 40));
    }

    private int meleeInterval() {
        double speed = BASE_ATTACK_SPEED + HumanoidWeapons.attackSpeedBonus(this.mob.getMainHandItem());
        return Math.max(2, (int) Math.round(TICKS_PER_SECOND / Math.max(0.1D, speed)));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
