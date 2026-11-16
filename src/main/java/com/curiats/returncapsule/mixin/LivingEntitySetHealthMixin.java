package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns raw health writes into ordinary damage.
 * <p>
 * Several hostile mods, SRP among them, do not attack through {@code hurt} at all: they call {@code setHealth} directly
 * (see {@code EntityParasiteBase.attackEntityAsMobMinimum}). That skips armour, blocking, the per-hit ceiling and every
 * other protective layer, and it skips {@code die} as well, so the entity falls over with a vanilla animation and no death
 * handling at all. Converting the write into a hit is the fix; clamping it was not.
 * <p>
 * Direct writes made from inside our own {@code hurt} are passed through untouched, otherwise the conversion would recurse.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySetHealthMixin {
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void returncapsule$cancelPassengerSetHealth(float health, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && player.getVehicle() instanceof TransportCapsuleEntity) {
            ci.cancel();
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$convertDirectHealthLoss(float health, CallbackInfo ci) {
        if (!((Object) this instanceof NeutralHumanoid humanoid) || humanoid.level().isClientSide) {
            return;
        }
        if (humanoid.isApplyingHit() || humanoid.bypassesProtections()) {
            return;   // a real hit, or an administrator: leave the write alone
        }
        float current = humanoid.getHealth();
        float loss = current - health;
        if (loss <= 0.0F) {
            return;   // healing and no-ops are handled by the heal lock
        }
        ci.cancel();
        humanoid.setConvertedHit(true);
        try {
            humanoid.hurt(humanoid.damageSources().generic(), loss);
        } finally {
            humanoid.setConvertedHit(false);
        }
    }
}
