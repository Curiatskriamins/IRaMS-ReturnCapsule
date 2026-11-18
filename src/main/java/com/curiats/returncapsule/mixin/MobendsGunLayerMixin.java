package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.mobends.MobendsActionGuard;
import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops MoBends from playing a swing animation while a humanoid holds a TaCZ gun.
 *
 * <p>The data parameter is declared as {@link Object} (a Mixin surrogate type) and the three action fields are
 * reached through {@link MobendsActionGuard} instead of {@code @Shadow}. This keeps MoBends an optional mod:
 * nothing in this file names a MoBends type, so the mod still compiles without the MoBends jar on the
 * classpath, while {@link Pseudo} plus {@code require = 0} keep it inert when MoBends is absent.</p>
 */
@Pseudo
@Mixin(targets = "goblinbob.mobends.standard.animation.bit.biped.item.BipedActionController", remap = false)
public abstract class MobendsGunLayerMixin {

    @Inject(method = "perform", at = @At("HEAD"), require = 0)
    private void returncapsule$handsOffForGuns(Object data, HumanoidArm primaryHand,
                                               ItemStack mainHand, ItemStack offHand, Item activeItem,
                                               CallbackInfo ci) {
        LivingEntity entity = MobendsActionGuard.entityOf(data);
        if (!(entity instanceof NeutralHumanoid humanoid)) {
            return;
        }
        if (!TaczRifles.isGun(humanoid.getMainHandItem())) {
            return;
        }
        humanoid.swinging = false;
        humanoid.swingTime = 0;
        MobendsActionGuard.clearAction(this);
    }
}
