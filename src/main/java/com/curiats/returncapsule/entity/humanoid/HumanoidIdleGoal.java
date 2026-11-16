package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * Movement for a humanoid that has nothing to fight: walk to an assigned point, follow its player, or patrol.
 * <p>
 * One goal with three modes rather than three competing goals, for the same reason the combat loop is one goal:
 * movement flags cannot be shared, and separate goals kept cancelling each other. The modes are checked in order —
 * assigned point first, then following, then patrol — and it only runs while there is no combat target.
 * <p>
 * Patrol walks a ring around the home point and pauses at each stop instead of drifting to random nearby blocks. Random
 * wandering reads as aimless; a sequence of stops at a fixed radius reads as watching an area, which is what a guard is
 * supposed to look like.
 */
public class HumanoidIdleGoal extends Goal {
    /** Distance at which a humanoid starts walking after its player. It stops again closer than {@link #FOLLOW_GAP}. */
    private static final double FOLLOW_START = 12.0D;
    /** Distance kept behind a followed player while walking: far enough not to crowd or shove them. */
    private static final double FOLLOW_GAP = 9.0D;
    /** Distance past which the follow path is walked at a run instead of a stroll. */
    private static final double CATCH_UP = 16.0D;
    /** Radius of the patrol ring around home. */
    private static final double PATROL_RADIUS = 8.0D;
    /** Ticks spent standing at each patrol stop: fifteen seconds, plus a little variation so stops do not sync up. */
    private static final int PATROL_PAUSE = 300;
    /** With a player this close the humanoid holds position instead of walking its round; circling next to someone
     *  watching looks restless rather than watchful. */
    private static final double ALERT_RADIUS = 12.0D;
    private static final int REPATH_INTERVAL = 10;

    private final NeutralHumanoid mob;
    private int repathCooldown;
    private int pauseTicks;
    /** Ticks until the next idle glance. */
    private int lookCooldown;
    /** Whether the follow hysteresis currently says walk. */
    private boolean following;
    /** Endpoint of the last issued path, so it is only replaced when it goes stale. */
    @Nullable
    private BlockPos lastTarget;
    private double patrolAngle;
    @Nullable
    private BlockPos patrolTarget;

    public HumanoidIdleGoal(NeutralHumanoid mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        this.patrolAngle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.getTarget() == null;
    }

    @Override
    public void tick() {
        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }
        this.glanceAround();
        BlockPos destination = this.mob.getDestination();
        if (destination != null) {
            if (this.mob.blockPosition().closerThan(destination, 2.0D)) {
                this.mob.setDestination(null);
                this.mob.getNavigation().stop();
                return;
            }
            this.follow(destination, 1.0D);
            return;
        }

        if (this.mob.isFollowEnabled()) {
            Player owner = this.nearestPlayer();
            if (owner != null) {
                double distanceSqr = this.mob.distanceToSqr(owner);
                // Hysteresis, so it does not stutter between walking and stopping at the threshold.
                if (!this.following && distanceSqr > FOLLOW_START * FOLLOW_START) {
                    this.following = true;
                } else if (this.following && distanceSqr < FOLLOW_GAP * FOLLOW_GAP) {
                    this.following = false;
                }
                if (this.following) {
                    boolean far = distanceSqr > CATCH_UP * CATCH_UP;
                    // Sprinting is decided in the entity; the path just has to be walked at a matching pace.
                    this.followEntity(owner, far ? 1.5D : 1.0D);
                    return;
                }
            } else {
                this.following = false;
            }
        }

        this.patrol();
    }

    /**
     * Idle head movement in the spirit of a player glancing around.
     * <p>
     * The vanilla goals only look at the nearest player or pick a random rotation, which reads as either fixed or stiff.
     * This sweeps the head to a point a few blocks away, offset to one side of where it already faces, every couple of
     * seconds — the same small, repeated re-focus a player does while standing still.
     */
    private void glanceAround() {
        if (this.lookCooldown > 0) {
            this.lookCooldown--;
            return;
        }
        this.lookCooldown = 40 + this.mob.getRandom().nextInt(80);
        double yaw = this.mob.getYHeadRot() + (this.mob.getRandom().nextDouble() - 0.5D) * 120.0D;
        double distance = 4.0D + this.mob.getRandom().nextDouble() * 6.0D;
        double x = this.mob.getX() - Math.sin(Math.toRadians(yaw)) * distance;
        double z = this.mob.getZ() + Math.cos(Math.toRadians(yaw)) * distance;
        double y = this.mob.getEyeY() + (this.mob.getRandom().nextDouble() - 0.5D) * 1.5D;
        this.mob.getLookControl().setLookAt(x, y, z, 30.0F, 30.0F);
    }

    /** Walks the patrol ring, pausing at every stop so the movement reads as watching rather than drifting. */
    private void patrol() {
        if (this.mob.level().getNearestPlayer(this.mob, ALERT_RADIUS) != null) {
            // Someone is close: stand and watch instead of continuing the round.
            this.pauseTicks = Math.max(this.pauseTicks, 40);
            this.mob.getNavigation().stop();
            return;
        }
        if (this.pauseTicks > 0) {
            this.pauseTicks--;
            this.mob.getNavigation().stop();
            return;
        }
        if (this.patrolTarget == null) {
            this.patrolTarget = this.nextPatrolPoint();
            return;
        }
        if (this.mob.blockPosition().closerThan(this.patrolTarget, 2.0D)) {
            this.patrolTarget = null;
            this.pauseTicks = PATROL_PAUSE + this.mob.getRandom().nextInt(120);
            this.mob.getNavigation().stop();
            return;
        }
        this.follow(this.patrolTarget, 1.0D);
    }

    /** Next stop on the ring, a quarter turn further round with a little jitter so the loop is not perfectly square. */
    private BlockPos nextPatrolPoint() {
        BlockPos home = this.mob.getHome();
        this.patrolAngle += Math.PI / 2.0D + (this.mob.getRandom().nextDouble() - 0.5D) * 0.5D;
        double radius = PATROL_RADIUS * (0.7D + this.mob.getRandom().nextDouble() * 0.3D);
        return BlockPos.containing(
                home.getX() + 0.5D + Math.cos(this.patrolAngle) * radius,
                home.getY(),
                home.getZ() + 0.5D + Math.sin(this.patrolAngle) * radius);
    }

    @Nullable
    private Player nearestPlayer() {
        double radius = HumanoidCombatRules.FOLLOW_RADIUS;
        List<Player> players = this.mob.level().getEntitiesOfClass(Player.class,
                this.mob.getBoundingBox().inflate(radius), Player::isAlive);
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : players) {
            if (player.isSpectator()) {
                continue;
            }
            double distance = this.mob.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /**
     * Follows a living target using the navigation's own entity tracking.
     * <p>
     * Pathing to the player's current block position meant a fresh path every few blocks, and each new path recomputes
     * the heading from wherever the mob happens to be, which shows up as the body swinging left and right while running.
     * {@code moveTo(LivingEntity, double)} keeps one path to a moving target instead.
     */
    private void followEntity(net.minecraft.world.entity.LivingEntity target, double speed) {
        if (!this.mob.getNavigation().isDone() && this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = REPATH_INTERVAL * 2;
        this.mob.getNavigation().moveTo(target, speed);
    }

    /**
     * Issues a path, but not on every tick.
     * <p>
     * Re-pathing every tick is what made a running humanoid weave instead of running in a straight line: each new path
     * restarts from the current position and slightly different heading, so the corrections stack up into a wobble. A
     * path is only replaced when the previous one finished or the throttle expired.
     */
    private void follow(BlockPos target, double speed) {
        // Re-path only when the old path is finished, or the target has moved far enough that the old endpoint is
        // useless. Issuing a new path every tick restarted the heading each time, which is what made a running humanoid
        // weave instead of running straight.
        boolean stale = this.lastTarget == null || this.lastTarget.distSqr(target) > 9.0D;
        if (!stale && !this.mob.getNavigation().isDone()) {
            return;
        }
        this.lastTarget = target;
        this.mob.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
    }
}
