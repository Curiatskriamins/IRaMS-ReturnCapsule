package com.curiats.returncapsule.client;

import com.curiats.returncapsule.item.StandardStorageAnchorItem;
import com.curiats.returncapsule.network.ModNetwork;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnchorInventoryToggle {

    private AnchorInventoryToggle() {
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1 || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot slot = hovered(screen, event.getMouseX(), event.getMouseY());
        if (slot == null) {
            return;
        }
        ItemStack stack = slot.getItem();
        if (!(stack.getItem() instanceof StandardStorageAnchorItem)) {
            return;
        }
        ModNetwork.toggleAnchorGuard(screen.getMenu().slots.indexOf(slot));
        event.setCanceled(true);
    }

    private static Slot hovered(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        double localX = mouseX - screen.getGuiLeft();
        double localY = mouseY - screen.getGuiTop();
        for (Slot slot : screen.getMenu().slots) {
            if (localX >= slot.x && localX < slot.x + 16 && localY >= slot.y && localY < slot.y + 16) {
                return slot;
            }
        }
        return null;
    }
}
