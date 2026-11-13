package com.curiats.returncapsule.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;

public final class LootLimits {
    public static boolean isMusicDisc(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof RecordItem) {
            return true;
        }
        String name = item.getClass().getName();
        if (name.contains("RecordItem") || name.contains("MusicDisc")) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && (id.getPath().contains("music_disc") || id.getPath().contains("record"));
    }

    public static int musicDiscCount(Container container) {
        int count = 0;
        for (int i = 0, size = container.getContainerSize(); i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (isMusicDisc(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static void trimMusicDiscs(Container container, int limit) {
        int count = 0;
        for (int i = 0, size = container.getContainerSize(); i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (!isMusicDisc(stack)) {
                continue;
            }
            int allowed = Math.max(0, limit - count);
            if (stack.getCount() > allowed) {
                stack.setCount(allowed);
                if (stack.isEmpty()) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
            count += stack.getCount();
        }
        container.setChanged();
    }

    private LootLimits() {
    }
}
