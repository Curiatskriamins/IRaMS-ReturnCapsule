package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.event.ForcedDisplacementGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ForcedMoveGuardMixin {

    @org.spongepowered.asm.mixin.Unique
    private static final double MAX_OWN_STEP = 0.4D;

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$guardForcedMove(double x, double y, double z, CallbackInfo info) {
        returncapsule$guardStep(x, y, z, info);
    }

    @Inject(method = "setPos(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void returncapsule$guardForcedMoveVec(net.minecraft.world.phys.Vec3 position, CallbackInfo info) {
        returncapsule$guardStep(position.x, position.y, position.z, info);
    }

    @org.spongepowered.asm.mixin.Unique
    private void returncapsule$guardStep(double x, double y, double z, CallbackInfo info) {
        if (!((Object) this instanceof LivingEntity living) || living.level().isClientSide) {
            return;
        }
        if (living.isPassenger() || living.isFallFlying()) {
            return;
        }
        if (living instanceof ServerPlayer player && player.isChangingDimension()) {
            return;
        }
        double dx = x - living.getX();
        double dz = z - living.getZ();
        double errorX = dx - living.getDeltaMovement().x;
        double errorZ = dz - living.getDeltaMovement().z;
        if (errorX * errorX + errorZ * errorZ <= MAX_OWN_STEP * MAX_OWN_STEP) {
            return;
        }
        if (!ForcedDisplacementGuard.hasGuard(living)) {
            return;
        }
        ForcedDisplacementGuard.Trace trace = ForcedDisplacementGuard.traceSource(living);
        if (!trace.exact()) {
            return;
        }
        info.cancel();
        if (living instanceof ServerPlayer player) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
        }
        ForcedDisplacementGuard.begin(living, trace);
    }
}
