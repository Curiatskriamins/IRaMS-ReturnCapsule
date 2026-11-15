package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.tacz.TaczRifles;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes MoBends treat a held firearm as a loaded crossbow so it plays its two-handed raised pose for it.
 * <p>
 * MoBends decides that pose in {@code HeldItemHelper.isLoadedCrossbow}, and a gun is not a crossbow, so the check is
 * always false: the item gets swapped to a crossbow hold, but the arm never comes up. That is the visible symptom —
 * "it holds a crossbow, but only a loaded one is actually raised".
 * <p>
 * Answering the check for guns is enough because every animation that follows only asks whether the held item is a
 * loaded crossbow; none of them re-inspect the item class.
 * <p>
 * {@code @Pseudo} plus {@code require = 0} keep this harmless when MoBends is absent or moves the method.
 */
@Pseudo
@Mixin(targets = "goblinbob.mobends.core.util.HeldItemHelper", remap = false)
public class MobendsLoadedCrossbowMixin {
    @Inject(method = "isLoadedCrossbow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$treatGunAsLoadedCrossbow(ItemStack stack,
                                                               CallbackInfoReturnable<Boolean> callback) {
        if (TaczRifles.isGun(stack)) {
            callback.setReturnValue(true);
        }
    }
}
