package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.util.AnchorField;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Effects of the standard storage anchor while it is carried: no conversions nearby, pets are not debuffed, and a full
 * anchor slows hostile projectiles without reducing their damage.
 *
 * <p>slowed projectiles are released once nothing keeps them slowed, and pet immunity is refreshed and
 * removed together with the field it came from instead of staying on the pet forever.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnchorFieldEvents {

    private static final String COTH_IMMUNITY = "srpcothimmunity";

    /** Pets that currently carry the SRP immunity flag because of an anchor field. */
    private static final Map<UUID, ResourceKey<Level>> FLAGGED_PETS = new ConcurrentHashMap<>();

    private static int tick;

    private AnchorFieldEvents() {
    }

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Pre event) {
        if (event.getEntity().level() instanceof ServerLevel level
                && AnchorField.covers(level, event.getEntity().position())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof TamableAnimal pet) || !(pet.level() instanceof ServerLevel level)) {
            return;
        }
        if (pet.getOwnerUUID() == null || !AnchorField.covers(level, pet.position())) {
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) {
            return;
        }
        if (PlayerEquipmentEffects.isBlocked(instance)) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        float multiplier = AnchorField.damageMultiplier(event.getSource().getDirectEntity(), event.getSource());
        if (multiplier > 1.0F) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 2 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        // Anything that left every field goes back to its normal speed before it can land.
        AnchorField.restoreLeftovers(server);
        for (ServerLevel level : server.getAllLevels()) {
            java.util.List<ServerPlayer> anchors = new java.util.ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && AnchorField.full(player)) {
                    anchors.add(player);
                }
            }
            if (anchors.isEmpty()) {
                continue;
            }
            // Overlapping fields would slow the same projectile several times; dedupe before touching its motion.
            java.util.Set<Projectile> projectiles = new java.util.HashSet<>();
            for (ServerPlayer player : anchors) {
                projectiles.addAll(level.getEntitiesOfClass(Projectile.class, AnchorField.area(player)));
            }
            for (Projectile projectile : projectiles) {
                AnchorField.slow(projectile, anchors);
            }
        }
        // SRP reads this flag from the entity itself; the set gets it on the player, pets get it here. Keep the flag
        // in sync with the field, otherwise a pet that visits an anchor once stays immune for its whole life.
        if (tick % 20 == 0) {
            refreshPetImmunity(server);
        }
    }

    private static void refreshPetImmunity(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !AnchorField.held(player)) {
                    continue;
                }
                for (TamableAnimal pet : level.getEntitiesOfClass(TamableAnimal.class, AnchorField.area(player))) {
                    if (pet.getOwnerUUID() == null) {
                        continue;
                    }
                    pet.getPersistentData().putInt(COTH_IMMUNITY, 1);
                    FLAGGED_PETS.put(pet.getUUID(), level.dimension());
                }
            }
        }
        Iterator<Map.Entry<UUID, ResourceKey<Level>>> iterator = FLAGGED_PETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ResourceKey<Level>> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getValue());
            if (level == null) {
                iterator.remove();
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof TamableAnimal pet) || pet.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (!AnchorField.covers(level, pet.position())) {
                pet.getPersistentData().remove(COTH_IMMUNITY);
                iterator.remove();
            }
        }
    }
}
