package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class LootRarity {
    public static int weight(SummonTier tier, ItemStack stack) {
        Rarity rarity = stack.getRarity();
        if (tier == SummonTier.BASIC) {
            if (rarity == Rarity.COMMON) {
                return 8;
            }
            return rarity == Rarity.UNCOMMON ? 2 : 0;
        }
        if (tier == SummonTier.ADVANCED) {
            if (rarity == Rarity.COMMON) {
                return 3;
            }
            if (rarity == Rarity.UNCOMMON) {
                return 6;
            }
            return rarity == Rarity.RARE ? 2 : 0;
        }
        if (rarity == Rarity.UNCOMMON) {
            return 3;
        }
        if (rarity == Rarity.RARE) {
            return 6;
        }
        return rarity == Rarity.EPIC ? 4 : 0;
    }

    private LootRarity() {
    }
}
