package com.curiats.returncapsule.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NetherStars {
    public static boolean has(Player player) {
        if (player.getOffhandItem().is(Items.NETHER_STAR)) {
            return true;
        }
        for (int i = 0, size = player.getInventory().getContainerSize(); i < size; i++) {
            if (player.getInventory().getItem(i).is(Items.NETHER_STAR)) {
                return true;
            }
        }
        return false;
    }

    public static void consumeOne(Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.NETHER_STAR)) {
            offhand.shrink(1);
            return;
        }
        player.getInventory().clearOrCountMatchingItems(
                stack -> stack.is(Items.NETHER_STAR), 1, player.getInventory());
    }

    private NetherStars() {
    }
}
