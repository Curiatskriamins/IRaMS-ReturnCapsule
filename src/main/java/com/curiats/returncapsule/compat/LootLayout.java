package com.curiats.returncapsule.compat;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
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
        // Pack from the first slot upwards like a vanilla chest. Spreading the stacks over all 324 slots left a hole
        // between almost every item, which read as an almost empty capsule even at a sixty percent fill target.
        int slot = 0;
        for (ItemStack stack : stacks) {
            while (slot < size && !container.getItem(slot).isEmpty()) {
                slot++;
            }
            if (slot >= size) {
                return;
            }
            container.setItem(slot, stack);
        }
        container.setChanged();
    }

    private LootLayout() {
    }
}
