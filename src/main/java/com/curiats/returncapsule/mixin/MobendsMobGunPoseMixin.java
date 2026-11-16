package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.mobends.GunPose;
import goblinbob.mobends.core.data.EntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the firearm pose at the single point every MoBends controller funnels through.
 * <p>
 * {@code Mutator.performAnimations} ends by running {@code data.getController().perform(data)}, so a hook here runs after
 * whichever controller the entity uses. Hooking a specific controller was the earlier mistake: the entity is not
 * necessarily driven by {@code HumanoidMobController}, and a mixin aimed at the wrong controller fails silently.
 * <p>
 * The pose is written after the controller, which makes it the last writer on those bones for this frame.
 */
@Pseudo
@Mixin(targets = "goblinbob.mobends.core.mutators.Mutator", remap = false)
public class MobendsMobGunPoseMixin {
    @Inject(method = "performAnimations", at = @At("TAIL"), require = 0)
    private void returncapsule$poseGun(EntityData<?> data, String animatedEntityKey, Object renderer,
                                       float partialTicks, CallbackInfo ci) {
        GunPose.apply(data);
    }
}
