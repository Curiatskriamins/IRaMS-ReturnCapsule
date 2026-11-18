package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class HumanoidAggroGuardMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void returncapsule$cannotTargetDowned(LivingEntity target, CallbackInfo ci) {
        if (target instanceof NeutralHumanoid humanoid && humanoid.isDowned()) {
            ci.cancel();
        }
    }
}
