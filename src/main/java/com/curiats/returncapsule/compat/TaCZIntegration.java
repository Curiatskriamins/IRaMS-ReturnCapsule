package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.tacz.TaczLootFiller;
import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraftforge.fml.ModList;

public final class TaCZIntegration {
    private static final String TACZ = "tacz";

    public static boolean isLoaded() {
        return ModList.get().isLoaded(TACZ);
    }

    public static boolean fill(Container container, RandomSource random, SummonTier tier) {
        if (!isLoaded()) {
            return false;
        }
        try {
            return TaczLootFiller.fill(container, random, tier);
        } catch (RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.warn("TaCZ integration failed; falling back to the vanilla capsule loot table", exception);
            return false;
        }
    }

    private TaCZIntegration() {
    }
}
