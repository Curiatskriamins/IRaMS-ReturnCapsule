package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.mobends.MobendsActionGuard;
import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import goblinbob.mobends.standard.data.BipedEntityData;
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
 * <p>The {@code BipedEntityData} parameter type comes from the compile only {@code compatStub} source set, not
 * from a MoBends jar: Mixin validates this handler descriptor exactly, so the parameter cannot be widened to
 * {@code Object}. The stub is never packaged, and no member is called on it - the entity and the action fields are
 * read reflectively through {@link MobendsActionGuard}, so nothing here has to resolve a MoBends member at
 * compile time. {@link Pseudo} plus {@code require = 0} keep the mixin inert when MoBends is absent.</p>
 */
@Pseudo
@Mixin(targets = "goblinbob.mobends.standard.animation.bit.biped.item.BipedActionController", remap = false)
public abstract class MobendsGunLayerMixin {

    @Inject(method = "perform", at = @At("HEAD"), require = 0)
    private void returncapsule$handsOffForGuns(BipedEntityData<?> data, HumanoidArm primaryHand,
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
