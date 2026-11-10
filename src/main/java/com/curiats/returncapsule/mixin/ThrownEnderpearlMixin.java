package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.PearlBoarding;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void returncapsule$boardBeforeTeleport(HitResult result, CallbackInfo ci) {
        if ((Object) this instanceof ThrownEnderpearl pearl
                && result instanceof BlockHitResult hit
                && PearlBoarding.tryBoard(pearl, hit)) {
            ci.cancel();
        }
    }
}
