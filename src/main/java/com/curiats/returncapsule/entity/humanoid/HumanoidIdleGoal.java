package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class HumanoidIdleGoal extends Goal {

    private static final double FOLLOW_START = 12.0D;

    private static final double FOLLOW_GAP = 9.0D;

    private static final double CATCH_UP = 16.0D;

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
                    this.following = true;
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
        this.mob.getNavigation().moveTo(target, speed);
    }

    private void follow(BlockPos target, double speed) {

        boolean stale = this.lastTarget == null || this.lastTarget.distSqr(target) > 9.0D;
        if (!stale && !this.mob.getNavigation().isDone()) {
            return;
        }
        this.lastTarget = target;
        this.mob.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
    }
}
