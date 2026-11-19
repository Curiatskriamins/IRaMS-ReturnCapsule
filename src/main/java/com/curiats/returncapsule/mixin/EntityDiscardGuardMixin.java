package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDiscardGuardMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void returncapsule$refuseDiscard(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!((Object) this instanceof NeutralHumanoid humanoid) || humanoid.level().isClientSide) {
            return;
        }

        ReturnCapsuleMod.LOGGER.debug("[humanoid] removal requested: {} (health {})", reason, humanoid.getHealth());

        boolean nearPlayer = humanoid.level().getNearestPlayer(humanoid, 64.0D) != null;
        boolean refused = reason == Entity.RemovalReason.DISCARDED
                || (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK && nearPlayer);
        if (!refused || humanoid.bypassesProtections()) {
            return;
        }
        if (humanoid.getHealth() <= 0.0F) {
            return;
        }
        ReturnCapsuleMod.LOGGER.debug("[humanoid] refused a discard while at {} health", humanoid.getHealth());
        ci.cancel();
    }
}
