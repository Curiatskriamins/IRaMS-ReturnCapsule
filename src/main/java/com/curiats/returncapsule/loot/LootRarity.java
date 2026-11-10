package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class LootRarity {
    public static int weight(SummonTier tier, ItemStack stack) {
        Rarity rarity = stack.getRarity();
        return switch (tier) {
            case BASIC -> switch (rarity) {
                case COMMON -> 4;
                case UNCOMMON -> 1;
                default -> 0;
            };
            case ADVANCED -> switch (rarity) {
                case COMMON -> 1;
                case UNCOMMON, RARE -> 2;
                default -> 0;
            };
            case ELITE -> switch (rarity) {
                case COMMON -> 1;
                case UNCOMMON -> 2;
                case RARE -> 3;
                case EPIC -> 4;
            };
        };
    }

    private LootRarity() {
    }
}
