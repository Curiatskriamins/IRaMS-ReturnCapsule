package com.curiats.returncapsule.menu;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.registry.ModMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CapsuleMenu extends AbstractContainerMenu {
    public static final int PAGE_SLOTS = 54;
    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int CONTAINER_TOP = 18;
    public static final int PLAYER_TOP = 140;
    public static final int HOTBAR_TOP = 198;
    private static final int PAGE_DATA_ID = 0;

    private final Container container;
    private int page;

    public CapsuleMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(Config.containerSlots()));
    }

    public CapsuleMenu(int id, Inventory inventory, Container container) {
        super(ModMenus.CAPSULE.get(), id);
        this.container = container;
        this.page = 0;
        container.startOpen(inventory.player);

        for (int i = 0; i < PAGE_SLOTS; i++) {
            this.addSlot(new PageSlot(container, i,
                    8 + (i % COLUMNS) * 18,
                    CONTAINER_TOP + (i / COLUMNS) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9,
                        8 + col * 18, PLAYER_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_TOP));
        }
    }

    public int getPage() {
        return this.page;
    }

    public int pages() {
        return Math.max(1, (this.container.getContainerSize() + PAGE_SLOTS - 1) / PAGE_SLOTS);
    }

    public void setPage(int page) {
        this.page = Math.floorMod(page, this.pages());
    }

    private int slotCount() {
        return this.container.getContainerSize();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 && id != 1) {
            return false;
        }
        this.setPage(this.page + (id == 0 ? -1 : 1));
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundContainerSetDataPacket(
                    this.containerId, PAGE_DATA_ID, this.page));
            NonNullList<ItemStack> items = NonNullList.withSize(PAGE_SLOTS, ItemStack.EMPTY);
            int base = this.page * PAGE_SLOTS;
            int slots = this.slotCount();
            for (int i = 0; i < PAGE_SLOTS; i++) {
                int index = base + i;
                if (index < slots) {
                    items.set(i, this.container.getItem(index));
                }
            }
            serverPlayer.connection.send(new ClientboundContainerSetContentPacket(
                    this.containerId, 0, items, this.getCarried()));
        }
        return true;
    }

    @Override
    public void setData(int id, int value) {
        if (id == PAGE_DATA_ID) {
            this.page = Math.max(0, Math.min(value, this.pages() - 1));
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < PAGE_SLOTS) {
            if (!this.moveItemStackTo(stack, PAGE_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, PAGE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    private class PageSlot extends Slot {
        PageSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        private int realIndex() {
            int index = page * PAGE_SLOTS + this.getContainerSlot();
            return index < slotCount() ? index : -1;
        }

        @Override
        public ItemStack getItem() {
            int index = this.realIndex();
            return index < 0 ? ItemStack.EMPTY : this.container.getItem(index);
        }

        @Override
        public void set(ItemStack stack) {
            int index = this.realIndex();
            if (index >= 0) {
                this.container.setItem(index, stack);
            }
            this.setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            int index = this.realIndex();
            return index < 0 ? ItemStack.EMPTY : this.container.removeItem(index, amount);
        }

        @Override
        public boolean hasItem() {
            return !this.getItem().isEmpty();
        }

        @Override
        public int getMaxStackSize() {
            return this.container.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(this.container.getMaxStackSize(), stack.getMaxStackSize());
        }
    }
}
