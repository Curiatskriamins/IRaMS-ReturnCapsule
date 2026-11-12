package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.loot.BulkCargo;
import com.curiats.returncapsule.loot.Supplies;
import com.curiats.returncapsule.loot.TieredLoot;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public final class TransportLoot {
    public static void fill(Container container, ServerLevel server, RandomSource random, SummonTier tier) {
        fillVanilla(container, server, random, tier);
        TieredLoot.fillModItems(container, tier, random);
        Supplies.fill(container, tier, random);
        BladeLoot.fill(container, tier, random);
        BulkCargo.fill(container, tier, random);
        if (TieredLoot.allowsGunLoot(tier) && TaCZIntegration.isLoaded()) {
            TaCZIntegration.fill(container, random, tier);
        }
        LootLayout.spread(container, random);
        com.curiats.returncapsule.util.LootLimits.trimMusicDiscs(container, 3);
    }

    public static void fillVanilla(Container container, ServerLevel server, RandomSource random, SummonTier tier) {
        LootTable table = server.getServer().getLootData().getLootTable(tier.lootTable());
        if (table == LootTable.EMPTY) {
            return;
        }
        LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(server.getSharedSpawnPos()))
                .create(LootContextParamSets.CHEST);
        List<ItemStack> rolled = new ArrayList<>(table.getRandomItems(params, random.nextLong()));
        
        
        
        rolled.sort(Comparator.comparingInt((ItemStack stack) -> stack.getRarity().ordinal()).reversed());
        int keep = Config.VANILLA_LOOT_STACKS.get();
        int limit = keep <= 0 ? rolled.size() : Math.min(keep, rolled.size());
        ItemInserter inserter = new ItemInserter(container);
        for (int i = 0; i < limit; i++) {
            inserter.insert(rolled.get(i));
        }
        container.setChanged();
    }

    private TransportLoot() {
    }
}
