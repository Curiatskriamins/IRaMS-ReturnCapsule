package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class LootRarity {
    public static int weight(SummonTier tier, ItemStack stack) {
        Rarity rarity = stack.getRarity();
        return switch (tier) {
            case BASIC -> switch (rarity) {
                case COMMON -> 8;
                case UNCOMMON -> 2;
                default -> 0;
            };
            case ADVANCED -> switch (rarity) {
                case COMMON -> 3;
                case UNCOMMON -> 6;
                case RARE -> 2;
                default -> 0;
            };
            case ELITE -> switch (rarity) {
                case COMMON -> 0;
                case UNCOMMON -> 3;
                case RARE -> 6;
                case EPIC -> 4;
            };
        };
    }

    private LootRarity() {
    }
}
