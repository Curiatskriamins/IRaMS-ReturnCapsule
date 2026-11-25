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

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LivingSetProtection {

    private static final String COTH_IMMUNITY = "srpcothimmunity";

    private static final java.util.UUID HEALTH_BONUS_ID =
            java.util.UUID.fromString("a1f0c3d2-8b47-4c1e-9a6d-5e2b7c4d1f30");

    private static void maintainHealthBonus(LivingEntity entity) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attribute =
                entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attribute == null || attribute.getModifier(HEALTH_BONUS_ID) != null) {
            return;
        }
        attribute.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                HEALTH_BONUS_ID, "returncapsule:equipment_mob", Config.EQUIPMENT_HEALTH_BONUS.get(),
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }

    private static void removeHealthBonus(LivingEntity entity) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attribute =
                entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(HEALTH_BONUS_ID);
        }
    }

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

    private static final java.util.Map<java.util.UUID, Long> BUDGET_SECOND = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<java.util.UUID, Float> BUDGET_TAKEN = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !PlayerEquipment.wearingFullSet(entity)) {
            return;
        }
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        float max = entity.getMaxHealth();
        float amount = Math.min(event.getAmount() * 0.5F, max * (float) Config.EQUIPMENT_HIT_CAP_FRACTION.get().floatValue());
        long second = entity.level().getGameTime() / 20L;
        long known = BUDGET_SECOND.getOrDefault(entity.getUUID(), -1L);
        float taken = known == second ? BUDGET_TAKEN.getOrDefault(entity.getUUID(), 0.0F) : 0.0F;
        float allowed = Math.min(amount, max * (float) Config.EQUIPMENT_SECOND_CAP_FRACTION.get().floatValue() - taken);
        if (allowed <= 0.0F) {
            event.setAmount(0.0F);
            return;
        }
        BUDGET_SECOND.put(entity.getUUID(), second);
        BUDGET_TAKEN.put(entity.getUUID(), taken + allowed);
        event.setAmount(allowed);
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
            maintainHealthBonus(entity);
        } else if (entity.getPersistentData().contains(COTH_IMMUNITY)) {
            entity.getPersistentData().remove(COTH_IMMUNITY);
            removeHealthBonus(entity);
        }
    }
}
