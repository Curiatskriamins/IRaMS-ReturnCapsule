package com.curiats.returncapsule.compat;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class LootLayout {
    public static void spread(Container container, RandomSource random) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
                container.setItem(i, ItemStack.EMPTY);
            }
        }
        if (stacks.isEmpty()) {
            return;
        }
        for (int i = stacks.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            ItemStack value = stacks.get(i);
            stacks.set(i, stacks.get(j));
            stacks.set(j, value);
        }
        int size = container.getContainerSize();
        int step = Math.max(1, size / stacks.size());
        int cursor = 0;
        for (ItemStack stack : stacks) {
            int slot = Math.min(size - 1, cursor * step + (step > 1 ? random.nextInt(step) : 0));
            while (slot < size && !container.getItem(slot).isEmpty()) {
                slot++;
            }
            if (slot >= size) {
                for (slot = 0; slot < size && !container.getItem(slot).isEmpty(); slot++) {
                }
            }
            if (slot >= size) {
                return;
            }
            container.setItem(slot, stack);
            cursor++;
        }
        container.setChanged();
    }

    private LootLayout() {
    }
}
