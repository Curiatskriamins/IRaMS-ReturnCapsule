package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refuses to let a healthy humanoid be discarded.
 * <p>
 * {@code Entity#setRemoved} carries a {@link Entity.RemovalReason}, which is exactly the distinction needed here:
 * <ul>
 *   <li>{@code UNLOADED_TO_CHUNK} / {@code UNLOADED_WITH_PLAYER} — the entity left because a chunk unloaded. Must be
 *       allowed, otherwise it would be pinned in memory and never saved or reloaded properly.</li>
 *   <li>{@code KILLED} — a normal death. Must be allowed; {@code die} already ran and dropped the loot.</li>
 *   <li>{@code CHANGED_DIMENSION} — teleported elsewhere. Allowed.</li>
 *   <li>{@code DISCARDED} — an outright removal that is not a death. This is what hostile content uses to make a mob
 *       disappear, and what made humanoids vanish without dropping anything or running their death blast.</li>
 * </ul>
 * So only {@code DISCARDED} is refused, and only while the entity still has health: a discard at zero health is the tail
 * of a normal death and is left alone.
 */
@Mixin(Entity.class)
public abstract class EntityDiscardGuardMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void returncapsule$refuseDiscard(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!((Object) this instanceof NeutralHumanoid humanoid) || humanoid.level().isClientSide) {
            return;
        }
        // Logged for every reason, not just the refused one: "it vanished and nothing was logged" has now happened twice,
        // and this line is what tells chunk unload, a normal death and an outright discard apart.
        ReturnCapsuleMod.LOGGER.info("[humanoid] removal requested: {} (health {})", reason, humanoid.getHealth());
        // A chunk does not unload underneath a player in vanilla, so an unload removal while one is standing nearby came
        // from something else and is refused the same way a discard is.
        boolean nearPlayer = humanoid.level().getNearestPlayer(humanoid, 64.0D) != null;
        boolean refused = reason == Entity.RemovalReason.DISCARDED
                || (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK && nearPlayer);
        if (!refused || humanoid.bypassesProtections()) {
            return;
        }
        if (humanoid.getHealth() <= 0.0F) {
            return;
        }
        ReturnCapsuleMod.LOGGER.info("[humanoid] refused a discard while at {} health", humanoid.getHealth());
        ci.cancel();
    }
}
