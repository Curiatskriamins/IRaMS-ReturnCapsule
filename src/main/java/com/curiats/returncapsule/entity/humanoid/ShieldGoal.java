package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

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

        this.block();
    }

    private void block() {

        if (this.mob.swinging) {
            if (this.mob.isUsingItem()) {
                this.mob.stopUsingItem();
            }
            return;
        }
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
