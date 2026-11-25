package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.Config;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kills living entities whose health ended up at zero or NaN without a death.
 *
 * <p>Checked every ten ticks: a single float comparison, but no need to run it on every tick of every entity.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthGuardMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void returncapsule$killInvalidHealth(CallbackInfo ci) {
        if (!Config.HEALTH_GUARD.get()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide || self.isRemoved() || self.isDeadOrDying()) {
            return;
        }
        if (self.tickCount % 10 != 0) {
            return;
        }
        float health = self.getHealth();
        if (Float.isNaN(health) || health <= 0.0F) {
            self.invulnerableTime = 0;
            self.die(self.damageSources().generic());
        }
    }
}
