package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a transport capsule passenger's health frozen while riding, so the fall cannot kill or heal them
 * through vanilla damage handling.
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
}
