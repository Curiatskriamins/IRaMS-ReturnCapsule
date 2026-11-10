package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.loot.TieredLoot;
import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;


public final class TransportLoot {
    public static void fill(Container container, ServerLevel server, RandomSource random, SummonTier tier) {
        if (TieredLoot.allowsGunLoot(tier) && TaCZIntegration.isLoaded()) {
            TaCZIntegration.fill(container, random);
        }
        fillVanilla(container, server, random, tier);
        TieredLoot.fillModItems(container, tier, random);
        LootLayout.spread(container, random);
    }

    public static void fillVanilla(Container container, ServerLevel server, RandomSource random, SummonTier tier) {
        LootTable table = server.getServer().getLootData().getLootTable(tier.lootTable());
        if (table == LootTable.EMPTY) {
            return;
        }
        LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(server.getSharedSpawnPos()))
                .create(LootContextParamSets.CHEST);
        table.fill(container, params, random.nextLong());
    }

    private TransportLoot() {
    }
}
