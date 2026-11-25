package com.curiats.returncapsule.block;

import com.curiats.returncapsule.entity.fall.CapsuleKind;
import com.curiats.returncapsule.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class CapsuleStructure {
    public static final int RADIUS = CapsuleKind.SUPPLY.radius();
    public static final int HEIGHT = CapsuleKind.SUPPLY.height();

    private static final int SUPPLY_PARTS = CapsulePartBlock.SUPPLY_PARTS;

    public static int placeParts(ServerLevel level, BlockPos main) {
        return placeParts(level, main, CapsuleKind.SUPPLY);
    }

    public static int placeParts(ServerLevel level, BlockPos main, CapsuleKind kind) {
        BlockState part = ModBlocks.CAPSULE_PART.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radius = kind.radius();
        int width = radius * 2 + 1;
        int base = kind == CapsuleKind.MANNED ? SUPPLY_PARTS : 0;
        int placed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = 0; dy < kind.height(); dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int index = base + dy * width * width + (dz + radius) * width + (dx + radius);
                    if (index < 0 || index > CapsulePartBlock.MAX_PART) {
                        // A kind/radius combination that does not fit PART would throw in setValue; skip instead.
                        continue;
                    }
                    cursor.setWithOffset(main, dx, dy, dz);
                    BlockState existing = level.getBlockState(cursor);
                    if (existing.isAir() || existing.canBeReplaced()) {
                        level.setBlock(cursor, part.setValue(CapsulePartBlock.PART, index), Block.UPDATE_ALL);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    public static void removeParts(Level level, BlockPos main) {
        removeParts(level, main, CapsuleKind.SUPPLY);
    }

    public static void removeParts(Level level, BlockPos main, CapsuleKind kind) {
        BlockState part = ModBlocks.CAPSULE_PART.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radius = kind.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = 0; dy < kind.height(); dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.setWithOffset(main, dx, dy, dz);
                    if (level.getBlockState(cursor).is(part.getBlock())) {
                        level.removeBlock(cursor, false);
                    }
                }
            }
        }
    }

    public static boolean isIntact(Level level, BlockPos main) {
        return isIntact(level, main, CapsuleKind.SUPPLY);
    }

    public static boolean isIntact(Level level, BlockPos main, CapsuleKind kind) {
        BlockState part = ModBlocks.CAPSULE_PART.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radius = kind.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = 0; dy < kind.height(); dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.setWithOffset(main, dx, dy, dz);
                    if (!level.getBlockState(cursor).is(part.getBlock())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** True for either capsule main block (the supply 3x3 one and the manned 5x5 one). */
    private static boolean isMain(BlockState state) {
        return state.is(ModBlocks.CAPSULE.get()) || state.is(ModBlocks.MANNED_CAPSULE.get());
    }

    public static CapsuleKind kindOf(BlockState mainState) {
        return mainState.is(ModBlocks.MANNED_CAPSULE.get()) ? CapsuleKind.MANNED : CapsuleKind.SUPPLY;
    }

    /**
     * Draft change: the main block is decoded from the part index instead of scanning the neighbourhood. A part's PART
     * value already stores its exact offset (3x3 layout for 0..17, 5x5x3 for 18..92), so this is unambiguous even when
     * two capsules stand next to each other, and it accepts the manned main block.
     */
    @Nullable
    public static BlockPos findMain(Level level, BlockPos probe) {
        BlockState probeState = level.getBlockState(probe);
        if (isMain(probeState)) {
            return probe;
        }
        if (!probeState.is(ModBlocks.CAPSULE_PART.get())) {
            return null;
        }
        int part = probeState.getValue(CapsulePartBlock.PART);
        int[] offset = CapsulePartBlock.offsetOf(part);
        BlockPos main = probe.offset(-offset[0], -offset[1], -offset[2]);
        BlockState mainState = level.getBlockState(main);
        if (!isMain(mainState)) {
            return null;
        }
        boolean mannedPart = part >= SUPPLY_PARTS;
        if (mainState.is(ModBlocks.MANNED_CAPSULE.get()) != mannedPart) {
            return null;
        }
        return main.immutable();
    }

    public static boolean crush(Level level, BlockPos pos) {
        BlockPos main = findMain(level, pos);
        if (main == null) {
            return false;
        }
        if (level.getBlockEntity(main) instanceof CapsuleBlockEntity container
                && level instanceof ServerLevel server) {
            container.ensureLootTable();
            Containers.dropContents(server, main, container);
            container.clearContent();
        }
        BlockState mainState = level.getBlockState(main);
        removeParts(level, main, kindOf(mainState));
        level.removeBlock(main, false);
        return true;
    }

    public static boolean isCapsuleStructure(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isMain(state)) {
            return true;
        }
        return state.is(ModBlocks.CAPSULE_PART.get()) && findMain(level, pos) != null;
    }

    private CapsuleStructure() {
    }
}
