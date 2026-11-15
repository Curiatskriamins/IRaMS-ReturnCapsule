package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.compat.tacz.TaczRifles;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The single combat loop: stance, movement and attack in one place.
 * <p>
 * Previous versions split this across a kite goal, a gun goal, a melee goal and conditional wrappers, all competing for
 * the same {@code MOVE}/{@code LOOK} flags. That produced contradictory behaviour by construction: a gunner inside
 * ten blocks was "kiting", which held the movement flags and stopped the gun goal from ever running, so it backed away
 * without shooting; a damage-triggered "hold melee" timer then refreshed itself every time it was hit in melee, so it
 * never picked the gun back up.
 * <p>
 * The rule set here is deliberately small and free of timers:
 * <ul>
 *   <li><b>Stance</b> is purely distance based, with hysteresis — beyond
 *       {@link HumanoidCombatRules#MELEE_REACH} plus a margin it holds the gun, inside the reach it holds the blade.
 *       No timeouts, no damage memory, nothing that can feed back into itself.</li>
 *   <li><b>Movement</b> is about range: a gunner that is too close steps back, a gunner out of range closes in, and a
 *       melee fighter walks into its reach. Backing off never blocks shooting.</li>
 *   <li><b>Attack</b> is independent of movement: it only needs line of sight, range and its own cooldown.</li>
 * </ul>
 */
public class HumanoidCombatGoal extends Goal {
    /** Extra distance past the melee reach before the gun comes back out, so the stance cannot flap at the boundary. */
    private static final double STANCE_HYSTERESIS = 1.5D;
    /** A gunner steps back when the target is closer than this fraction of its engagement range. */
    private static final double MIN_GUN_RANGE = 5.0D;
    private static final double APPROACH_SPEED = 1.0D;
    /** How long the humanoid steps back after landing a melee hit, in ticks. */
    private static final int STEP_BACK_TICKS = 8;
    /** Speed of that step back: slower than the approach, so it reads as a hop rather than a retreat. */
    private static final float STEP_BACK_SPEED = 0.6F;
    /** Degrees of body rotation per tick while closing on a target: faster than the look control, but still a turn. */
    private static final float TURN_PER_TICK = 25.0F;
    /** Half-angle of the melee swing cone: 75 degrees each side, generous without reaching behind the back. */
    private static final double ATTACK_CONE_COS = Math.cos(Math.toRadians(75.0D));
    /** Speed while backpedalling: slower than walking forward, like a player. */
    private static final float RETREAT_SPEED_FACTOR = 0.7F;
    /** Ticks between navigation updates. Re-pathing every tick makes the body swing left and right. */
    private static final int REPATH_INTERVAL = 10;
    /** How long a fight may make no progress before the target is dropped. */
    private static final int STALL_TIMEOUT = 200;
    private static final double BASE_ATTACK_SPEED = 4.0D;
    private static final double TICKS_PER_SECOND = 20.0D;

    private final NeutralHumanoid mob;
    private int attackCooldown;
    /** Navigation is only re-issued this often; calling it every tick makes the body jerk left and right. */
    private int repathCooldown;
    /** Ticks left in the post-hit step back. */
    private int stepBackTicks;
    /** Target bookkeeping so a fight that makes no progress can be abandoned. */
    private int targetId = -1;
    private float lastTargetHealth = -1.0F;
    private int stalledTicks;

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

    /**
     * Drops a target that is not taking damage. Invulnerable turrets were the visible case: the humanoid locked on,
     * produced no damage for minutes and therefore never picked anything else, which reads as "it only fights one mob".
     */
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

    /** Gunner movement: keep a workable distance, but never as a substitute for shooting. */
    private void holdRange(LivingEntity target, double distance) {
        if (distance < MIN_GUN_RANGE) {
            // Backpedal instead of turning around: the body keeps facing the target, the movement control walks it
            // backwards. That is what a player does while firing, and it also stops the body from swivelling between the
            // walk direction and the aim direction.
            this.faceTarget(target);
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().strafe(-1.0F, 0.0F);
            this.mob.setSpeed(RETREAT_SPEED_FACTOR);
        } else if (distance > this.mob.engagementRadiusFor(target)) {
            this.navigateTo(target, APPROACH_SPEED);
        } else {
            this.mob.getNavigation().stop();
        }
    }

    /** Points body and head at the target without any smoothing. */
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

    /**
     * Melee movement is a two-beat cycle: step in, hit, step back out.
     * <p>
     * Standing in contact and swinging forever looks nothing like a player, who steps into the swing and then backs off
     * again to avoid the trade. The step back also costs the target a hit it would otherwise land while the humanoid
     * waits out its attack cooldown.
     */
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

    /** Melee movement: walk into reach and stop there. */
    private void closeIn(LivingEntity target, double distance) {
        if (distance > HumanoidCombatRules.MELEE_REACH) {
            this.navigateTo(target, APPROACH_SPEED);
        } else {
            this.mob.getNavigation().stop();
        }
    }

    /** True when it is time to issue a new path: either the old one finished, or the throttle expired. */
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
                return;
            }
            this.attackCooldown = this.gunInterval();
            this.mob.fireGun(target);
            return;
        }
        if (distance > HumanoidCombatRules.MELEE_REACH) {
            return;
        }
        // Only swing at what is actually in front. Without this the humanoid dealt damage to things behind its back
        // while the body was still turning, which is both wrong on screen and plainly unfair.
        if (!this.facing(target)) {
            return;
        }
        this.attackCooldown = this.meleeInterval();
        this.stepBackTicks = STEP_BACK_TICKS;
        this.mob.setAggressive(true);
        this.mob.swing(InteractionHand.MAIN_HAND);
        this.mob.doHurtTarget(target);
    }

    /**
     * Turns the body toward the target at a fixed maximum rate, faster than the vanilla look control but still a turn
     * rather than a snap. The body yaw is driven directly because the look control only moves the head, and melee reach
     * and the swing cone are both judged against the body.
     */
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

    /**
     * True when the target lies inside a forward cone. Minecraft yaw 0 faces +Z and +90 faces -X, so the body's look
     * direction is {@code (-sin(yaw), cos(yaw))}.
     */
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

    /** Trigger cadence from the gun's own RPM, so the AI never becomes the rate limiter. */
    private int gunInterval() {
        int rpm = TaczRifles.rpm(this.mob.getMainHandItem());
        if (rpm <= 0) {
            // TaCZ's synced shoot cooldown already gates the trigger. When the RPM is unknown the AI must not add a
            // second, slower limit of its own — that was why firing felt far slower than a player's.
            return 1;
        }
        return Math.max(1, Math.min(Math.round(1200.0F / rpm), 40));
    }

    /** Swing cadence from the weapon's attack speed, like a player's cooldown. */
    private int meleeInterval() {
        double speed = BASE_ATTACK_SPEED + HumanoidWeapons.attackSpeedBonus(this.mob.getMainHandItem());
        return Math.max(2, (int) Math.round(TICKS_PER_SECOND / Math.max(0.1D, speed)));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
