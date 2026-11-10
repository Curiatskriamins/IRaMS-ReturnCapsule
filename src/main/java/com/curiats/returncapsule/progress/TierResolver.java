package com.curiats.returncapsule.progress;

import com.curiats.returncapsule.compat.BeaconRange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public final class TierResolver {
    public static SummonTier resolve(ServerLevel level, BlockPos anchor, @Nullable Player player) {
        WorldProgress progress = WorldProgress.get(level.getServer());
        if (player instanceof ServerPlayer serverPlayer) {
            progress.syncFrom(serverPlayer);
        }
        if (!progress.bossDefeated()) {
            return SummonTier.BASIC;
        }
        return BeaconRange.isActiveNear(level, anchor) ? SummonTier.ELITE : SummonTier.ADVANCED;
    }

    private TierResolver() {
    }
}
