package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

 
public final class BulkCargo {
    public static void fill(Container container, SummonTier tier, RandomSource random) {
        double target = Config.FILL_TARGET.get();
        if (target <= 0.0D) {
            return;
        }
        int size = container.getContainerSize();
        int goal = (int) Math.round(size * target);
        ItemInserter inserter = new ItemInserter(container);
        int guard = goal * 2 + size;
        while (occupied(container) < goal && guard-- > 0) {
            ItemStack stack = ModItemPool.random(random, tier);
            if (stack.isEmpty()) {
                return;
            }
            stack.setCount(Math.max(1, Math.min(countFor(tier), stack.getMaxStackSize())));
            if (!inserter.insert(stack)) {
                return;
            }
        }
        container.setChanged();
    }

    private static int countFor(SummonTier tier) {
        return switch (tier) {
            case BASIC -> 3;
            case ADVANCED -> 8;
            case ELITE -> 16;
        };
    }

    private static int occupied(Container container) {
        int count = 0;
        for (int i = 0, size = container.getContainerSize(); i < size; i++) {
            if (!container.getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private BulkCargo() {
    }
}
