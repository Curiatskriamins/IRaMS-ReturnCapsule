package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;

public final class TieredLoot {
    public static boolean allowsGunLoot(SummonTier tier) {
        return tier != SummonTier.BASIC;
    }

    public static void fillModItems(Container container, SummonTier tier, RandomSource random) {
        ModItemPool.fill(container, tier, random, Config.MOD_ITEM_ROLLS.get());
    }

    private TieredLoot() {
    }
}
