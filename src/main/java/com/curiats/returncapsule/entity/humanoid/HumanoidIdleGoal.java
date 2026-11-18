package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

@SuppressWarnings({"this-escape"})
public class HumanoidIdleGoal extends Goal {

    private static final int MAX_SAFE_DROP = 3;

    private static final double FOLLOW_START = 12.0D;

    private static final double FOLLOW_GAP = 6.0D;

    private static final double CATCH_UP = 12.0D;

    private static final int FOLLOW_DECISION_CHANCE = 20;

    private static final double PATROL_RADIUS = 8.0D;

    private static final int PATROL_PAUSE = 300;

    private static final double ALERT_RADIUS = 12.0D;
    private static final int REPATH_INTERVAL = 10;

    private final NeutralHumanoid mob;
    private int repathCooldown;
    private int pauseTicks;

    private int lookCooldown;

    private boolean following;

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
        if (this.mob.isDowned()) {
            return false;
        }
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

                if (!this.following && distanceSqr > FOLLOW_START * FOLLOW_START) {
                    if (this.mob.getRandom().nextInt(FOLLOW_DECISION_CHANCE) == 0) {
                        this.following = true;
                    }
                } else if (this.following && distanceSqr < FOLLOW_GAP * FOLLOW_GAP) {
                    this.following = false;
                }
                if (this.following) {
                    boolean far = distanceSqr > CATCH_UP * CATCH_UP;

                    this.followEntity(owner, far ? 1.5D : 1.0D);
                    return;
                }
            } else {
                this.following = false;
            }
        }

        this.patrol();
    }

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

    private void patrol() {
        if (this.mob.level().getNearestPlayer(this.mob, ALERT_RADIUS) != null) {

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

    private void followEntity(net.minecraft.world.entity.LivingEntity target, double speed) {
        if (!this.mob.getNavigation().isDone() && this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = REPATH_INTERVAL * 2;
        net.minecraft.core.BlockPos ladder = this.ladderDown(target);
        if (ladder != null) {
            this.mob.requestMove(ladder.getX() + 0.5D, ladder.getY(), ladder.getZ() + 0.5D, speed, 1);
            return;
        }
        if (!this.mob.walkDirect(target.getX(), target.getY(), target.getZ(), speed)) {
            this.mob.requestMove(target, speed, 1);
        }
    }

    private net.minecraft.core.BlockPos ladderDown(net.minecraft.world.entity.LivingEntity target) {
        if (target.getY() > this.mob.getY() - 2.0D) {
            return null;
        }
        net.minecraft.core.BlockPos base = this.mob.blockPosition();
        for (net.minecraft.core.BlockPos candidate : net.minecraft.core.BlockPos.betweenClosed(
                base.offset(-4, -1, -4), base.offset(4, 1, 4))) {
            if (!HumanoidTraversal.isClimbable(this.mob.level(), candidate)) {
                continue;
            }
            net.minecraft.core.BlockPos below = candidate.below();
            if (HumanoidTraversal.isClimbable(this.mob.level(), below)) {
                continue;
            }
            if (candidate.getY() < base.getY() - 1 || candidate.getY() > base.getY() + 2) {
                continue;
            }
            int drop = HumanoidTraversal.ladderDrop(this.mob.level(), candidate);
            if (drop > MAX_SAFE_DROP) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private void follow(BlockPos target, double speed) {

        boolean stale = this.lastTarget == null || this.lastTarget.distSqr(target) > 9.0D;
        if (!stale && !this.mob.getNavigation().isDone()) {
            return;
        }
        this.lastTarget = target;
        this.mob.requestMove(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed, 1);
    }
}
