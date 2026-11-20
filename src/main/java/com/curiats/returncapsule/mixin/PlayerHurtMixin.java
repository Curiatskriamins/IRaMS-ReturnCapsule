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
 * Remembers who dealt the last hit to a player.
 *
 * <p>A direct health write carries no source, so the converted hit needs one to blame. Nothing else is tracked here:
 * telling a real hit's health write from a direct one is done by the invulnerability window instead of a flag, because
 * a flag left set by an exception would silently disable the whole set.</p>
 */
@Mixin(Player.class)
public abstract class PlayerHurtMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void returncapsule$beginHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            PlayerEquipment.remember(player, source);
            PlayerEquipment.markHurt(player);
        }
    }
}
