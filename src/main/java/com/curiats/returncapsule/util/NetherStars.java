package com.curiats.returncapsule.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The nether star is this mod's shared "expensive key": it gates both the tier III supply summon and
 * every transport capsule launch, so the lookup and the payment live here instead of being duplicated
 * by each item that needs them.
 */
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

    /** Consumes one nether star, taking it from the offhand first so the payment stays predictable. */
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
