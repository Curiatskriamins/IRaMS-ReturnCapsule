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
    private static final java.util.concurrent.atomic.AtomicLong TEMP_WRITE_LOG =
            new java.util.concurrent.atomic.AtomicLong();
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
        // Direction first: LivingEntity's constructor raises health to the max before Player has an inventory, so a
        // heal check must not touch equipment at all.
        float loss = player.getHealth() - health;
        if (loss <= 0.0F) {
            return;
        }
        // Vanilla's own health write happens inside the hurt call, which stamped this tick. A direct write from a mob
        // can happen on any tick, so the stamp is the only reliable discriminator - invulnerability time is not, since
        // direct writes usually land while the player is still invulnerable.
        if (PlayerEquipment.insideHurt(player) || player.isDeadOrDying() || player.isInvulnerable()) {
            return;
        }
        boolean setWorn = Config.EQUIPMENT_SET_ENABLED.get() && PlayerEquipment.wearingFullSet(player);
        if (TEMP_WRITE_LOG.incrementAndGet() <= 60) {
            com.curiats.returncapsule.ReturnCapsuleMod.LOGGER.info("[equipment] direct write loss={} insideHurt={} set={} health={}",
                    loss, PlayerEquipment.insideHurt(player), setWorn, player.getHealth());
        }
        if (!setWorn) {
            return;
        }
        ci.cancel();
        if (health <= 0.0F) {
            // A write to zero is a kill attempt, not a free execution: it runs through the same ladder so the ceiling,
            // the per second budget and last stand all apply, and vanilla death still happens if the reduced hit really
            // is lethal.
            PlayerEquipment.convert(player, player.getHealth());
            return;
        }
        PlayerEquipment.convert(player, loss);
    }

    private static final ThreadLocal<Boolean> CONVERTING = ThreadLocal.withInitial(() -> false);

    /**
     * Pets and other wearers: a direct health write from a parasite skips armour and the hurt path just like it does
     * for players, so replay it through hurt. Vanilla damage inside hurt is excluded with hurtTime and a re-entry flag.
     */
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$convertWearerHealthLoss(float health, CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity living)
                || living instanceof ServerPlayer
                || living instanceof NeutralHumanoid
                || living.level().isClientSide) {
            return;
        }
        if (CONVERTING.get() || living.hurtTime > 0 || living.isDeadOrDying() || living.isInvulnerable()) {
            return;
        }
        float loss = living.getHealth() - health;
        if (loss <= 0.0F) {
            return;
        }
        if (!Config.EQUIPMENT_SET_ENABLED.get() || !PlayerEquipment.wearingFullSet(living)) {
            return;
        }
        ci.cancel();
        CONVERTING.set(true);
        try {
            LivingEntity attacker = living.getLastHurtByMob();
            float amount = health <= 0.0F ? living.getHealth() : loss;
            living.hurt(attacker == null
                    ? living.damageSources().generic()
                    : living.damageSources().mobAttack(attacker), amount);
        } finally {
            CONVERTING.set(false);
        }
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
