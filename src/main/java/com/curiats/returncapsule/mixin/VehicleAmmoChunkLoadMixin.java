package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.vehicle.VehicleAmmoGate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the vehicle mod from force loading a chunk for every projectile that leaves the simulated area.
 *
 * <p>{@code EntityUtil.keepChunkLoaded} adds two radius three region tickets (a 7x7 chunk area) per call, and missiles,
 * rockets, aerial bombs and artillery shells call it on every tick their next position is outside the entity ticking
 * area. Cancelling it here means the round freezes in unloaded terrain and resumes when a player comes near, unless
 * {@link VehicleAmmoGate} decides the round is a locked target missile or a long range strike.</p>
 *
 * <p>Both handler parameters are vanilla types, so no stub of the vehicle mod is needed; {@link Pseudo} plus
 * {@code require = 0} keep this inert when the vehicle mod is absent.</p>
 */
@Pseudo
@Mixin(targets = "org.ywzj.vehicle.util.EntityUtil", remap = false)
public class VehicleAmmoChunkLoadMixin {

    @Inject(method = "keepChunkLoaded", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$gateAmmoChunkLoad(Entity entity, Vec3 position, CallbackInfo ci) {
        if (VehicleAmmoGate.shouldFreeze(entity)) {
            ci.cancel();
        }
    }
}
