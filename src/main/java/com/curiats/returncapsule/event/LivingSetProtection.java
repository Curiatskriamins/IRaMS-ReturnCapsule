package com.curiats.returncapsule.event;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.PlayerEquipment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The standard issue armour protects any wearer, not only players; a pet wearing the full set gets the same harmful
 * effect filter, the same flat mitigation and the same SRP conversion flag.
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LivingSetProtection {

    private static final String COTH_IMMUNITY = "srpcothimmunity";

    private LivingSetProtection() {
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (!Config.EQUIPMENT_EFFECT_IMMUNITY.get()) {
            return;
        }
        LivingEntity living = event.getEntity();
        if (living instanceof Player) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !PlayerEquipment.wearingFullSet(living)) {
            return;
        }
        if (PlayerEquipmentEffects.isBlocked(instance)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !PlayerEquipment.wearingFullSet(entity)) {
            return;
        }
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        event.setAmount(event.getAmount() * 0.5F);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || entity.level().isClientSide || entity.tickCount % 20 != 0) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        if (PlayerEquipment.wearingFullSet(entity)) {
            entity.getPersistentData().putInt(COTH_IMMUNITY, 1);
        } else if (entity.getPersistentData().contains(COTH_IMMUNITY)) {
            entity.getPersistentData().remove(COTH_IMMUNITY);
        }
    }
}
