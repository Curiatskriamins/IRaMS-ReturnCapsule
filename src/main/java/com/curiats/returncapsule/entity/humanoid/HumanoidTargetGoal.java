package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

@SuppressWarnings({"this-escape"})
public class HumanoidTargetGoal extends Goal {

    private static final double MAX_SCAN = 64.0D;

    private static final int MAX_CANDIDATES = 64;

    private static final double CLOSE_RADIUS = 16.0D;
    private static final int CLOSE_INTERVAL = 5;

    private final NeutralHumanoid mob;
    private int wideCooldown;
    private int closeCooldown;

    private static final int RETARGET_INTERVAL = 20;
    @Nullable
    private LivingEntity chosen;

    public HumanoidTargetGoal(NeutralHumanoid mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));

        this.wideCooldown = Math.floorMod(mob.getId(), HumanoidCombatRules.SCAN_INTERVAL);
    }

    @Override
    public boolean canUse() {

        LivingEntity current = this.mob.getTarget();
        if (current != null) {
            if (current.isAlive() && this.mob.tickCount % RETARGET_INTERVAL != 0) {
                return false;
            }
            if (!current.isAlive()) {
                this.mob.setTarget(null);
            }
        }
        if (this.closeCooldown > 0) {
            this.closeCooldown--;
        } else {
            this.closeCooldown = CLOSE_INTERVAL;
            LivingEntity near = this.findTarget(CLOSE_RADIUS);
            if (near != null) {
                this.chosen = near;
                return true;
            }
        }
        if (this.wideCooldown > 0) {
            this.wideCooldown--;
            return false;
        }
        this.wideCooldown = HumanoidCombatRules.SCAN_INTERVAL;
        this.chosen = this.findTarget(HumanoidCombatRules.SCAN_RADIUS);
        return this.chosen != null;
    }

    @Override
    public void start() {
        if (this.chosen != null) {
            LivingEntity current = this.mob.getTarget();
            boolean keepCurrent = current != null && current.isAlive()
                    && !HumanoidCombatRules.outranks(this.mob, this.chosen, current);
            if (!keepCurrent) {
                this.mob.setTarget(this.chosen);
            }
        }
        this.chosen = null;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Nullable
    private LivingEntity findTarget(double radius) {
        LivingEntity best = null;
        LivingEntity fallback = null;
        java.util.Set<LivingEntity> engaged = engagedTargets();

        int examined = 0;
        for (LivingEntity candidate : this.mob.level().getEntitiesOfClass(
                LivingEntity.class, this.scanArea(radius), this::isCandidate)) {
            if (++examined > MAX_CANDIDATES) {
                break;
            }
            if (!this.worthEngaging(candidate)) {
                continue;
            }
            if (HumanoidCombatRules.outranks(this.mob, candidate, best)) {
                if (!engaged.contains(candidate)) {
                    best = candidate;
                } else if (fallback == null) {
                    fallback = candidate;
                }
            }
        }

        if (radius >= HumanoidCombatRules.SCAN_RADIUS && this.mob.level() instanceof ServerLevel server) {
            for (UUID id : HumanoidSquad.active(server)) {
                Entity shared = server.getEntity(id);
                if (!(shared instanceof LivingEntity candidate) || !this.isCandidate(candidate)) {
                    continue;
                }
                if (!this.worthEngaging(candidate)) {
                    continue;
                }
                if (HumanoidCombatRules.outranks(this.mob, candidate, best)) {
                    best = candidate;
                }
            }
        }
        return best != null ? best : fallback;
    }

    private java.util.Set<LivingEntity> engagedTargets() {
        java.util.Set<LivingEntity> engaged = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        for (NeutralHumanoid other : this.mob.level().getEntitiesOfClass(NeutralHumanoid.class,
                this.mob.getBoundingBox().inflate(64.0D))) {
            if (other != this.mob && other.getTarget() != null) {
                engaged.add(other.getTarget());
            }
        }
        return engaged;
    }

    private AABB scanArea(double radius) {
        double vertical = Math.min(radius, HumanoidCombatRules.SCAN_VERTICAL_RADIUS);
        return this.mob.getBoundingBox().inflate(radius, vertical, radius);
    }

    private boolean worthEngaging(LivingEntity candidate) {
        double radius = Math.min(this.mob.engagementRadiusFor(candidate), MAX_SCAN);
        return this.mob.distanceToSqr(candidate) <= radius * radius;
    }

    private boolean isCandidate(LivingEntity candidate) {
        if (candidate == this.mob || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }

        if (candidate.isInvulnerable()) {
            return false;
        }

        if (candidate instanceof Player || candidate instanceof NeutralHumanoid) {
            return false;
        }

        if (!HumanoidCombatRules.isHostileEnemy(candidate)
                && !HumanoidCombatRules.threatensAllies(candidate)) {
            return false;
        }
        return HumanoidSight.canSee(this.mob.level(), this.mob, candidate);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }
}
