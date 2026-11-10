package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;

public final class TieredLoot {
    private static final int MOD_ITEM_ROLLS = 5;

    public static boolean allowsGunLoot(SummonTier tier) {
        return tier != SummonTier.BASIC;
    }

    public static void fillModItems(Container container, SummonTier tier, RandomSource random) {
        ModItemPool.fill(container, tier, random, MOD_ITEM_ROLLS);
    }

    private TieredLoot() {
    }
}
