package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Picks what a humanoid should fight, if anything.
 * <p>
 * Selection is not "nearest hostile": the group is meant to be smart about what is worth the walk. Every candidate is
 * filtered through {@link HumanoidCombatRules#engagementRadius}, which scales with the target's own stats (a zombie is
 * only worth 30 blocks, a Warden 300), and then ranked by {@link HumanoidCombatRules#priority} so anything already
 * attacking a player or a humanoid wins.
 * <p>
 * Two scan rates are combined. A cheap close-range sweep runs often, so finishing one mob leads straight into the next
 * one standing nearby instead of leaving the humanoid idle until the next wide sweep; the expensive 500-block sweep runs
 * on a slow cadence and is what brings distant Warden-grade threats in. Every accepted target is announced to
 * {@link HumanoidSquad} so the rest of the group inherits it.
 */
public class HumanoidTargetGoal extends Goal {
    /** Close sweep: small box, checked often, so kills chain. */
    private static final double CLOSE_RADIUS = 16.0D;
    private static final int CLOSE_INTERVAL = 5;

    private final NeutralHumanoid mob;
    private int wideCooldown;
    private int closeCooldown;
    @Nullable
    private LivingEntity chosen;

    public HumanoidTargetGoal(NeutralHumanoid mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        // Stagger the first wide sweep so a group does not all scan the world on the same tick.
        this.wideCooldown = Math.floorMod(mob.getId(), HumanoidCombatRules.SCAN_INTERVAL);
    }

    @Override
    public boolean canUse() {
        // Retaliation and anything already being fought keep priority over fresh target picking.
        LivingEntity current = this.mob.getTarget();
        if (current != null) {
            if (current.isAlive()) {
                return false;
            }
            // Defensive: a dead target must never block the next acquisition.
            this.mob.setTarget(null);
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
            this.mob.setTarget(this.chosen);
            // No squad broadcast when a target is picked. Sharing made the whole group converge on one enemy, so a pack
            // of parasites was fought one at a time while the rest attacked freely; each humanoid now picks its own.
            // Alerts are still shared when someone is actually hurt, which is when help matters.
        }
        this.chosen = null;
    }

    /** One-shot goal: it only decides who to fight, the movement and attack goals take over from there. */
    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Nullable
    private LivingEntity findTarget(double radius) {
        LivingEntity best = null;
        double bestPriority = -Double.MAX_VALUE;

        for (LivingEntity candidate : this.mob.level().getEntitiesOfClass(
                LivingEntity.class, this.scanArea(radius), this::isCandidate)) {
            if (!this.worthEngaging(candidate)) {
                continue;
            }
            double priority = HumanoidCombatRules.priority(this.mob, candidate);
            if (priority > bestPriority) {
                bestPriority = priority;
                best = candidate;
            }
        }

        // Anything a comrade already spotted counts as seen, even outside this humanoid's own scan box.
        if (radius >= HumanoidCombatRules.SCAN_RADIUS && this.mob.level() instanceof ServerLevel server) {
            for (UUID id : HumanoidSquad.active(server)) {
                Entity shared = server.getEntity(id);
                if (!(shared instanceof LivingEntity candidate) || !this.isCandidate(candidate)) {
                    continue;
                }
                if (!this.worthEngaging(candidate)) {
                    continue;
                }
                double priority = HumanoidCombatRules.priority(this.mob, candidate);
                if (priority > bestPriority) {
                    bestPriority = priority;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * Wide horizontally, deliberately shallow vertically. Inflating a box by 500 in every axis makes the entity section
     * lookup walk thousands of empty chunk columns and dozens of vertical sections; clipping Y to a sane band keeps the
     * scan cheap without losing anything a ground fight cares about.
     */
    private AABB scanArea(double radius) {
        double vertical = Math.min(radius, HumanoidCombatRules.SCAN_VERTICAL_RADIUS);
        return this.mob.getBoundingBox().inflate(radius, vertical, radius);
    }

    private boolean worthEngaging(LivingEntity candidate) {
        double radius = this.mob.engagementRadiusFor(candidate);
        return this.mob.distanceToSqr(candidate) <= radius * radius;
    }

    private boolean isCandidate(LivingEntity candidate) {
        if (candidate == this.mob || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        // Invulnerable things (turrets and the like) would otherwise lock the humanoid in a fight it can never win or
        // finish, which looks exactly like "it attacks one thing and then stops".
        if (candidate.isInvulnerable()) {
            return false;
        }
        // Players and other humanoids are never picked up proactively; players stay a retaliation-only affair.
        if (candidate instanceof Player || candidate instanceof NeutralHumanoid) {
            return false;
        }
        // Either it is a genuinely hostile mob, or it just attacked somebody on our side. Value alone is not enough:
        // a neutral Enderman or an iron golem is left alone.
        return HumanoidCombatRules.isHostileEnemy(candidate)
                || HumanoidCombatRules.threatensAllies(candidate);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }
}
