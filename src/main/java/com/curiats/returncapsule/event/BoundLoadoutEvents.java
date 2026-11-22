package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.util.BoundLoadout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the bound standard issue set on its owner.
 *
 * <p>Everything here is driven by state changes (equipment change, drops, respawn, login) plus two cheap periodic
 * passes: a five tick void lift for bound item entities and a five second armour check that also covers mods which
 * destroy items without firing an event, such as the trash slot.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BoundLoadoutEvents {

    private record Tracked(ResourceKey<Level> dimension, UUID id) {
    }

    private static final Set<Tracked> ITEMS = ConcurrentHashMap.newKeySet();

    private static int tick;

    private BoundLoadoutEvents() {
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getSlot().getType() != EquipmentSlot.Type.ARMOR) {
            return;
        }
        if (!BoundLoadout.isBoundTo(event.getFrom(), player) || BoundLoadout.isBoundTo(event.getTo(), player)) {
            return;
        }
        // Non player removal: the occupied slot (if any) is dropped and the bound piece goes back in.
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(() -> BoundLoadout.restoreArmor(player));
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean removed = event.getDrops().removeIf(drop -> BoundLoadout.isBoundTo(drop.getItem(), player));
        if (removed) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.execute(() -> {
                    BoundLoadout.restoreArmor(player);
                    BoundLoadout.restoreSword(player);
                });
            }
        }
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = event.getItem().getItem();
        if (BoundLoadout.isBound(stack) && !BoundLoadout.isBoundTo(stack, player)) {
            // Bound pieces only ever go back to their owner.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        // The player may drop their own set on purpose; nothing to do, but it is tracked for the void lift.
        ItemEntity item = event.getEntity();
        if (BoundLoadout.isBound(item.getItem())) {
            track(item);
        }
    }

    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        if (BoundLoadout.isBound(item.getItem())) {
            track(item);
        }
    }

    @SubscribeEvent
    public static void onItemLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        ITEMS.remove(new Tracked(event.getLevel().dimension(), item.getUUID()));
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        CompoundTag target = event.getEntity().getPersistentData();
        for (String key : new String[]{"returncapsule_loadout_stacks", "returncapsule_loadout_granted",
                "returncapsule_loadout_opened"}) {
            if (original.contains(key, Tag.TAG_ANY_NUMERIC) || original.contains(key)) {
                target.put(key, original.get(key).copy());
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BoundLoadout.restoreArmor(player);
            BoundLoadout.restoreSword(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BoundLoadout.restoreArmor(player);
            BoundLoadout.restoreSword(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        if (tick % 5 == 0) {
            liftVoidItems(event.getServer());
        }
        if (tick % 100 == 0) {
            // Safety net for anything destroyed without an event (trash slot, third party containers).
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                BoundLoadout.restoreArmor(player);
            }
        }
    }

    private static void track(ItemEntity item) {
        ITEMS.add(new Tracked(item.level().dimension(), item.getUUID()));
    }

    /** Bound pieces that fall out of the world float back up instead of being discarded. */
    private static void liftVoidItems(MinecraftServer server) {
        if (ITEMS.isEmpty()) {
            return;
        }
        ITEMS.removeIf(key -> {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                return true;
            }
            if (!(level.getEntity(key.id()) instanceof ItemEntity item) || !item.isAlive()) {
                return true;
            }
            if (!BoundLoadout.isBound(item.getItem())) {
                return true;
            }
            double floor = level.getMinBuildHeight() + 2.0D;
            if (item.getY() < floor) {
                item.setPos(item.getX(), floor, item.getZ());
                item.setDeltaMovement(0.0D, 0.25D, 0.0D);
                item.hasImpulse = true;
            }
            return false;
        });
    }
}
