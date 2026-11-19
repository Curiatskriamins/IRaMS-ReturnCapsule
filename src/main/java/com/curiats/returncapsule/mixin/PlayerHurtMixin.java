package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.PlayerEquipment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the span of a real {@code hurt} call and records who dealt it.
 *
 * <p>The health write inside {@code hurt} must not be converted again, otherwise every ordinary hit would loop
 * through the conversion; the flag tells {@link LivingEntitySetHealthMixin} to leave it alone. The attacker is
 * remembered here because a later direct write carries no source of its own.</p>
 */
@Mixin(Player.class)
public abstract class PlayerHurtMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void returncapsule$beginHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerEquipment.setHurting(true);
        if ((Object) this instanceof ServerPlayer player) {
            PlayerEquipment.remember(player, source);
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void returncapsule$endHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerEquipment.setHurting(false);
    }
}
