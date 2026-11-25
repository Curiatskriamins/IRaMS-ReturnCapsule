package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.item.TransportDesignatorItem;
import com.curiats.returncapsule.registry.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TransportCharge {
    private static final int RECHARGE_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || event.player.tickCount % RECHARGE_INTERVAL_TICKS != 0) {
            return;
        }
        charge(event.player.getInventory(), event.player);
        if (event.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            charge(serverPlayer.getEnderChestInventory(), serverPlayer);
        }
    }

    private static void charge(Container container, net.minecraft.world.entity.player.Player eventPlayer) {
        int max = Config.designatorMaxCharge();
        for (int i = 0, size = container.getContainerSize(); i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.is(ModItems.TRANSPORT_DESIGNATOR.get())) {
                continue;
            }
            int current = TransportDesignatorItem.charge(stack);
            if (current < max) {
                TransportDesignatorItem.setCharge(stack, current + 1);
                container.setChanged();
                if (eventPlayer != null) {
                    eventPlayer.inventoryMenu.broadcastChanges();
                }
            }
        }
    }

    private TransportCharge() {
    }
}
