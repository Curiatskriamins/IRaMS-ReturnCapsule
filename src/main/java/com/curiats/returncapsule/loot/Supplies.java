package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

 
public final class Supplies {
    private static final Item[] FOODS = {
            Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.BREAD,
            Items.BAKED_POTATO, Items.COOKED_CHICKEN, Items.COOKED_MUTTON
    };

    public static void fill(Container container, SummonTier tier, RandomSource random) {
        ItemInserter inserter = new ItemInserter(container);
        fillFood(inserter, tier, random);
        ArmorSets.fill(container, tier, random);
        inserter.insert(new ItemStack(swordFor(tier)));
        inserter.insert(new ItemStack(Items.SHIELD));
        container.setChanged();
    }

    private static void fillFood(ItemInserter inserter, SummonTier tier, RandomSource random) {
        int stacks = switch (tier) {
            case BASIC -> 1;
            case ADVANCED -> 2;
            case ELITE -> 3;
        };
        for (int i = 0; i < stacks; i++) {
            Item food = FOODS[random.nextInt(FOODS.length)];
            inserter.insert(new ItemStack(food, 16 + random.nextInt(49)));
        }
    }

    private static Item swordFor(SummonTier tier) {
        return switch (tier) {
            case BASIC -> Items.IRON_SWORD;
            case ADVANCED -> Items.DIAMOND_SWORD;
            case ELITE -> Items.NETHERITE_SWORD;
        };
    }

    private Supplies() {
    }
}
