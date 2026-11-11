package com.curiats.returncapsule.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class ItemInserter {
    private final Container container;
    private final int size;
    private int cursor;

    public ItemInserter(Container container) {
        this.container = container;
        this.size = container.getContainerSize();
    }

    public boolean insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        int remaining = stack.getCount();
        int maxStack = Math.max(1, stack.getMaxStackSize());
        
        
        for (int i = 0; i < this.size && remaining > 0; i++) {
            ItemStack existing = this.container.getItem(i);
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack)) {
                continue;
            }
            int room = Math.min(existing.getMaxStackSize(), maxStack) - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining);
            existing.grow(moved);
            this.container.setItem(i, existing);
            remaining -= moved;
        }
        while (remaining > 0) {
            int slot = this.nextEmptySlot();
            if (slot < 0) {
                return false;
            }
            int amount = Math.min(maxStack, remaining);
            ItemStack copy = stack.copy();
            copy.setCount(amount);
            this.container.setItem(slot, copy);
            remaining -= amount;
        }
        return true;
    }

    public boolean hasRoom() {
        return this.peekEmptySlot() >= 0;
    }

    private int nextEmptySlot() {
        int slot = this.peekEmptySlot();
        if (slot >= 0) {
            this.cursor = slot + 1;
        }
        return slot;
    }

    private int peekEmptySlot() {
        while (this.cursor < this.size && !this.container.getItem(this.cursor).isEmpty()) {
            this.cursor++;
        }
        return this.cursor < this.size ? this.cursor : -1;
    }

    public static void clear(Container container) {
        for (int i = 0, size = container.getContainerSize(); i < size; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
    }
}
