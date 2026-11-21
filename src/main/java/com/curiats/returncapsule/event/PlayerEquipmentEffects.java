package com.curiats.returncapsule.event;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.PlayerEquipment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerEquipmentEffects {

    private static final Set<UUID> GRANTED_REGENERATION = ConcurrentHashMap.newKeySet();

    private static final String COTH_IMMUNITY = "srpcothimmunity";

    /** Fixed id so the modifier can always be found and removed again. */
    private static final UUID HEALTH_BONUS_ID = UUID.fromString("d4c1a8b9-5f6e-4b3a-9c7d-2e1f0a9b8c7d");

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!Config.EQUIPMENT_EFFECT_IMMUNITY.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !PlayerEquipment.wearingFullSet(player)) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) {
            return;
        }
        if (isBlocked(instance)) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static boolean isBlocked(MobEffectInstance instance) {
        MobEffect effect = instance.getEffect();
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        String key = id == null ? "" : id.toString();
        List<? extends String> blocked = Config.EQUIPMENT_BLOCKED_EFFECTS.get();
        for (String entry : blocked) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            if (key.equals(trimmed) || key.startsWith(trimmed + ":") || key.startsWith(trimmed)) {
                return true;
            }
        }
        return !effect.isBeneficial();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        boolean fullSet = PlayerEquipment.wearingFullSet(player);
        // Equivalent health to a base humanoid: two hundred points with the same five per second regeneration, so the
        // set does not just reduce damage but actually survives a sustained fight the way the humanoid does.
        maintainHealthBonus(player, fullSet);
        if (fullSet && player.getHealth() < player.getMaxHealth()) {
            float perTick = (float) (Config.EQUIPMENT_HEALTH_REGEN.get() / 20.0D);
            if (perTick > 0.0F) {
                player.heal(perTick);
            }
        }
        if (player.tickCount % 20 != 0) {
            return;
        }

        if (Config.EQUIPMENT_COTH_IMMUNITY.get()) {
            if (fullSet) {
                player.getPersistentData().putInt(COTH_IMMUNITY, 1);
            } else if (player.getPersistentData().contains(COTH_IMMUNITY)) {
                player.getPersistentData().remove(COTH_IMMUNITY);
            }
        }

        boolean wanted = Config.EQUIPMENT_REGEN_ENABLED.get()
                && fullSet
                && PlayerEquipment.holdingBlade(player);
        if (wanted) {
            GRANTED_REGENERATION.add(id);
            MobEffectInstance current = player.getEffect(MobEffects.REGENERATION);
            int amplifier = Config.EQUIPMENT_REGEN_AMPLIFIER.get();
            if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 60) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, amplifier,
                        true, false, false));
            }
            return;
        }
        if (!GRANTED_REGENERATION.remove(id)) {
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.REGENERATION);
        if (current != null && current.isAmbient()
                && current.getAmplifier() == Config.EQUIPMENT_REGEN_AMPLIFIER.get()) {
            player.removeEffect(MobEffects.REGENERATION);
        }
    }

    private static void maintainHealthBonus(ServerPlayer player, boolean fullSet) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        boolean present = attribute.getModifier(HEALTH_BONUS_ID) != null;
        if (fullSet && !present) {
            // Permanent, not transient: a transient modifier is not written to player data, so every login started
            // from the unbuffed max health and then climbed back up as the tick re-added it - which read as the set
            // "healing again" on every visit to the save.
            attribute.addPermanentModifier(new AttributeModifier(HEALTH_BONUS_ID, "returncapsule:equipment",
                    Config.EQUIPMENT_HEALTH_BONUS.get(), AttributeModifier.Operation.ADDITION));
        } else if (!fullSet && present) {
            attribute.removeModifier(HEALTH_BONUS_ID);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    private PlayerEquipmentEffects() {
    }
}
