package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.tacz.TaczRifles;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "goblinbob.mobends.core.util.HeldItemHelper", remap = false)
public class MobendsLoadedCrossbowMixin {
    @Inject(method = "isLoadedCrossbow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$gunIsLoadedCrossbow(ItemStack stack,
                                                          CallbackInfoReturnable<Boolean> callback) {
        if (TaczRifles.isGun(stack)) {
            callback.setReturnValue(true);
        }
    }
}
