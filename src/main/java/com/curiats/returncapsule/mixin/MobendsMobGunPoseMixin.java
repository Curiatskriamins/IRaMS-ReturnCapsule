package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.mobends.GunPose;
import goblinbob.mobends.core.data.LivingEntityData;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "goblinbob.mobends.core.mutators.Mutator", remap = false)
public class MobendsMobGunPoseMixin {
    @SuppressWarnings({"rawtypes"})
    @Inject(method = "performAnimations", at = @At("TAIL"), require = 0)
    private void returncapsule$poseGun(LivingEntityData data, String animatedEntityKey, LivingEntityRenderer renderer,
                                       float partialTicks, CallbackInfo ci) {
        GunPose.apply(data);
    }
}
