package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.util.DamageLock;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks healing while a victim still owes health to percentage damage.
 * <p>
 * Trimming the amount was not enough: a victim at 146 kept returning to 154, because trimming only changes one call and
 * leaves every other healing path, and every ordering of tick, damage and regen, free to put the health back. Cancelling
 * the call instead — the approach {@code nocubesarmory}'s {@code NoHealMixin} uses — makes the health taken by percentage
 * damage genuinely unrecoverable.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealLockMixin {
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$blockHealing(float amount, CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity living) || living.level().isClientSide) {
            return;
        }
        if (DamageLock.locked(living, living.level().getGameTime()) <= 0.0F) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$blockHealthRestore(float health, CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity living) || living.level().isClientSide) {
            return;
        }
        if (health <= living.getHealth()) {
            return;   // damage still flows through normally
        }
        float locked = DamageLock.locked(living, living.level().getGameTime());
        if (locked <= 0.0F) {
            return;
        }
        if (health > Math.max(0.0F, living.getMaxHealth() - locked)) {
            ci.cancel();
        }
    }
}
