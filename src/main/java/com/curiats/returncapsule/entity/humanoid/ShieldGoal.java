package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Raises the off-hand shield when a threat is within {@link HumanoidCombatRules#SHIELD_DISTANCE}.
 * <p>
 * A mob blocks through the same vanilla path a player does: {@code startUsingItem} on the shield makes
 * {@code isBlocking()} true, and vanilla then decides whether a given hit is blocked by direction. The item only has to
 * be recognised as a shield, which {@link HumanoidWeapons#isShield} does by class or by block use-animation so that
 * shields from other mods work too.
 * <p>
 * This goal deliberately declares no {@link Flag}s, so it can run alongside the movement and attack goals instead of
 * taking their slots away.
 */
public class ShieldGoal extends Goal {
    private final NeutralHumanoid mob;

    public ShieldGoal(NeutralHumanoid mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive() || !this.mob.hasShield()) {
            return false;
        }
        // Only while fighting at arm's length. A raised shield while firing looks wrong, and the gunner is not trading
        // blows anyway.
        if (!this.mob.isMeleeStance()) {
            return false;
        }
        double range = HumanoidCombatRules.SHIELD_DISTANCE;
        return this.mob.distanceToSqr(target) <= range * range;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.block();
    }

    @Override
    public void tick() {
        // Re-assert every tick: an attack or an equipment update calls stopUsingItem, which silently ends the block and
        // left the shield doing nothing most of the time.
        this.block();
    }

    private void block() {
        if (!this.mob.isBlocking()) {
            this.mob.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    @Override
    public void stop() {
        if (this.mob.isUsingItem()) {
            this.mob.stopUsingItem();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
