package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.compat.PlayerEquipment;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySetHealthMixin {
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void returncapsule$cancelPassengerSetHealth(float health, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && player.getVehicle() instanceof TransportCapsuleEntity) {
            ci.cancel();
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$convertPlayerHealthLoss(float health, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (PlayerEquipment.isHurting() || !Config.EQUIPMENT_SET_ENABLED.get()
                || !PlayerEquipment.wearingFullSet(player)) {
            return;
        }
        float loss = player.getHealth() - health;
        if (loss <= 0.0F) {
            return;
        }
        ci.cancel();
        if (health <= 0.0F) {
            PlayerEquipment.execute(player);
            return;
        }
        PlayerEquipment.convert(player, loss);
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$convertDirectHealthLoss(float health, CallbackInfo ci) {
        if (!((Object) this instanceof NeutralHumanoid humanoid) || humanoid.level().isClientSide) {
            return;
        }
        if (humanoid.isApplyingHit() || humanoid.bypassesProtections()) {
            return;
        }
        float loss = humanoid.getHealth() - health;
        if (loss <= 0.0F) {
            return;
        }
        if (health <= 0.0F) {
            // A write straight to zero is a kill, not a hit. Converting it would halve the loss and leave the entity
            // alive at half health, while letting it through unchanged would leave a zero health corpse that never ran
            // the vanilla death path (setHealth does not call die).
            ci.cancel();
            humanoid.dieFromDirectHealthWrite(humanoid.damageSources().mobAttack(humanoid.recentAttacker()));
            return;
        }
        ci.cancel();
        humanoid.setConvertedHit(true);
        try {
            // mob_attack, not generic: generic is in the vanilla bypasses_armor tag, which silently skipped the
            // humanoid's armour and toughness for every converted hit. The remembered attacker also keeps aggro and
            // the death message intact, since a direct health write carries no source of its own.
            humanoid.hurt(humanoid.damageSources().mobAttack(humanoid.recentAttacker()), loss);
        } finally {
            humanoid.setConvertedHit(false);
        }
    }
}
