package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.util.BoundLoadout;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No creature may pick up a bound standard issue piece. */
@Mixin(Mob.class)
public abstract class MobPickupGuardMixin {

    @Inject(method = "canHoldItem", at = @At("HEAD"), cancellable = true)
    private void returncapsule$refuseBound(ItemStack stack, CallbackInfoReturnable<Boolean> info) {
        if (BoundLoadout.isBound(stack)) {
            info.setReturnValue(false);
        }
    }
}
