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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Effect side of the standard issue set.
 *
 * <p>Two rules:</p>
 * <ul>
 * <li>the full set immunises debuffs, and also effects that present themselves as beneficial but are the parasite
 *     mod's conversion vectors. Those are matched by namespace/id from config, so a pack can add its own;</li>
 * <li>holding the blade in either hand while wearing the full set grants regeneration, refreshed once a second and
 *     removed again when the hold ends.</li>
 * </ul>
 *
 * <p>Blocking the conversion effects is also what keeps a player from being converted: the transformation is driven by
 * those effects, so denying them denies the transformation itself.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerEquipmentEffects {

    private static final Set<UUID> GRANTED_REGENERATION = ConcurrentHashMap.newKeySet();

    /** The parasite mod's own conversion immunity tag, the one the humanoid entity also carries. */
    private static final String COTH_IMMUNITY = "srpcothimmunity";

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
        if (!(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        UUID id = player.getUUID();
        boolean fullSet = PlayerEquipment.wearingFullSet(player);

        // The parasite mod gates its conversion effect on this exact NBT tag (the humanoid sets it in its own
        // constructor), so the set grants it while worn and removes it again afterwards.
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

    private PlayerEquipmentEffects() {
    }
}
