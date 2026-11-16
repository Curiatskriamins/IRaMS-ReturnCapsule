package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
            return;
        }
        float current = humanoid.getHealth();
        float loss = current - health;
        if (loss <= 0.0F) {
            return;
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
