package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.util.HiddenPlayers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySetHealthMixin {
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void returncapsule$cancelHiddenSetHealth(float health, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && (HiddenPlayers.isHidden(player) || player.getVehicle() instanceof TransportCapsuleEntity)) {
            ci.cancel();
        }
    }
}
