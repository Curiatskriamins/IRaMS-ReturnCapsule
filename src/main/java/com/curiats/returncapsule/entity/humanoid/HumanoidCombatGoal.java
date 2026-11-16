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

    private static final double MIN_GUN_RANGE = 5.0D;
    private static final double APPROACH_SPEED = 1.0D;

    private static final int STEP_BACK_TICKS = 8;

    private static final float STEP_BACK_SPEED = 0.6F;

    private static final float TURN_PER_TICK = 25.0F;

    private static final double ATTACK_CONE_COS = Math.cos(Math.toRadians(75.0D));

    private static final float RETREAT_SPEED_FACTOR = 0.7F;

    private static final int REPATH_INTERVAL = 10;

    private static final int STALL_TIMEOUT = 200;

    private static final int NO_SIGHT_TIMEOUT = 60;
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

    private int repositionTicks;

    private int lastSweepParticle = -100;

    private static final int SWEEP_PARTICLE_INTERVAL = 10;

    public HumanoidCombatGoal(NeutralHumanoid mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
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
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.abandonIfStalled(target)) {
            return;
        }
        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }
        double distance = Math.sqrt(this.mob.distanceToSqr(target));

        boolean gun = this.mob.shouldHoldGun(distance, STANCE_HYSTERESIS);
        this.mob.useStance(gun ? NeutralHumanoid.Stance.RANGED : NeutralHumanoid.Stance.MELEE);

        if (this.mob.isRangedStance()) {
            this.mob.chooseGunFor(distance);
            this.holdRange(target, distance);
        } else {
            this.turnToward(target);
            this.meleeMovement(target, distance);
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
            return false;
        }
        if (++this.stalledTicks <= STALL_TIMEOUT) {
            return false;
        }
        this.stalledTicks = 0;
        this.targetId = -1;
        this.mob.setTarget(null);
        return true;
    }

    private void holdRange(LivingEntity target, double distance) {
        if (distance < MIN_GUN_RANGE) {

            this.faceTarget(target);
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().strafe(-1.0F, 0.0F);
            this.mob.setSpeed(RETREAT_SPEED_FACTOR);
        } else if (distance > this.mob.engagementRadiusFor(target)) {
            this.navigateTo(target, APPROACH_SPEED);
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

    private static final double RETREAT_DISTANCE = 18.0D;

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

    private void navigateTo(LivingEntity target, double speed) {
        if (this.shouldRepath()) {
            this.mob.getNavigation().moveTo(target, speed);
        }
    }

    private void attack(LivingEntity target, double distance) {
        if (this.mob.isRangedStance()) {
            if (!this.mob.getSensing().hasLineOfSight(target)) {

            if (++this.noSightTicks > NO_SIGHT_TIMEOUT) {
                this.noSightTicks = 0;
                this.mob.setTarget(null);
                return;
            }
                return;
            }
            this.attackCooldown = this.gunInterval();
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
