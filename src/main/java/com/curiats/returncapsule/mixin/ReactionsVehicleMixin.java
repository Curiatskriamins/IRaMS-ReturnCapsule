package com.curiats.returncapsule.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollifiedreactions.client.ReactionDetector", remap = false)
public class ReactionsVehicleMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$skipWhileRiding(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && player.isPassenger()) {
            ci.cancel();
        }
    }
}
