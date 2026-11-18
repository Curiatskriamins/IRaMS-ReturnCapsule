package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import goblinbob.mobends.core.animation.layer.HardAnimationLayer;
import goblinbob.mobends.core.util.HeldItemHelper;
import goblinbob.mobends.standard.AttackActionType;
import goblinbob.mobends.standard.animation.bit.biped.CrossbowHoldAnimationBit;
import goblinbob.mobends.standard.data.BipedEntityData;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "goblinbob.mobends.standard.animation.bit.biped.item.BipedActionController", remap = false)
public abstract class MobendsGunLayerMixin {
    @Shadow
    protected HardAnimationLayer<BipedEntityData<?>> layerAction;

    @Shadow
    protected AttackActionType currentAttackActionType;

    @Shadow
    protected goblinbob.mobends.core.animation.bit.AnimationBit<BipedEntityData<?>> actionBit;

    @Inject(method = "perform", at = @At("HEAD"), require = 0)
    private void returncapsule$handsOffForGuns(BipedEntityData<?> data, HumanoidArm primaryHand,
                                               ItemStack mainHand, ItemStack offHand, Item activeItem,
                                               CallbackInfo ci) {
        if (!(data.getEntity() instanceof NeutralHumanoid humanoid)) {
            return;
        }
        boolean gun = TaczRifles.isGun(humanoid.getMainHandItem());
        if (!gun) {
            return;
        }
        humanoid.swinging = false;
        humanoid.swingTime = 0;
        this.layerAction.clearAnimation();
        this.actionBit = null;
        this.currentAttackActionType = null;
    }
}
