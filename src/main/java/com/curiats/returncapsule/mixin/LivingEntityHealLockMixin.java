package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.util.DamageLock;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealLockMixin {
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$blockHealing(float amount, CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity living) || living.level().isClientSide) {
            return;
        }
        float locked = DamageLock.locked(living, living.level().getGameTime());
        if (locked <= 0.0F) {
            return;
        }
        // Healing is allowed up to the ceiling the seal leaves, not blocked outright.
        float ceiling = Math.max(0.0F, living.getMaxHealth() - locked);
        if (living.getHealth() >= ceiling) {
            ci.cancel();
            return;
        }
        if (living.getHealth() + amount > ceiling) {
            living.setHealth(ceiling);
            ci.cancel();
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$blockHealthRestore(float health, CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity living) || living.level().isClientSide) {
            return;
        }
        if (health <= living.getHealth()) {
            return;
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
