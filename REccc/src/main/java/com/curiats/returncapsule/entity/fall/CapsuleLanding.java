package com.curiats.returncapsule.entity.fall;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.CapsuleFire;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The single landing path shared by every capsule type.
 *
 * <p>Placement, part structure and fire are identical; the type only contributes its footprint. Like the original
 * supply capsule, a capsule is always placed: the parts only replace air and replaceable blocks.</p>
 */
public final class CapsuleLanding {

    public static boolean land(ServerLevel level, BlockPos landing, Direction facing, CapsuleKind kind,
                               Container inventory, SummonTier tier) {
        // Same behaviour as the supply capsule: place regardless of what occupies the footprint, because the parts only
        // replace air and replaceable blocks. canPlace stays available for callers that want a non destructive check.
        CapsulePlacer.withItems(level, landing, facing, inventory, tier, kind);
        spreadFire(level, landing);
        return mainPlaced(level, landing, kind);
    }

    public static boolean landWithLootTable(ServerLevel level, BlockPos landing, Direction facing, CapsuleKind kind,
                                            net.minecraft.resources.ResourceLocation lootTable, SummonTier tier) {
        CapsulePlacer.withLootTable(level, landing, facing, lootTable, tier);
        spreadFire(level, landing);
        return mainPlaced(level, landing, kind);
    }

    /** True when the whole footprint can be built without replacing anything solid. */
    public static boolean canPlace(ServerLevel level, BlockPos landing, CapsuleKind kind) {
        int radius = kind.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy < kind.height(); dy++) {
                    BlockPos pos = landing.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.canBeReplaced() && !state.isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void spreadFire(ServerLevel level, BlockPos landing) {
        // Draft change: the original supply/transport implementations used y + 1.0 as the fire centre; atCenterOf is
        // half a block lower and changes which block faces catch fire.
        CapsuleFire.spread(level, new Vec3(landing.getX() + 0.5D, landing.getY() + 1.0D, landing.getZ() + 0.5D),
                Config.LANDING_FIRE_RADIUS.get(), Config.LANDING_FIRE_CHANCE.get());
    }

    /** Draft change: lets the caller detect a failed placement instead of always reporting success. */
    private static boolean mainPlaced(ServerLevel level, BlockPos landing, CapsuleKind kind) {
        Block expected = kind == CapsuleKind.MANNED ? ModBlocks.MANNED_CAPSULE.get() : ModBlocks.CAPSULE.get();
        return level.getBlockState(landing).is(expected);
    }

    private CapsuleLanding() {
    }
}
