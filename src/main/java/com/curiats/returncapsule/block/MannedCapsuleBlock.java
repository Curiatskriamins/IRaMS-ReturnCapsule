package com.curiats.returncapsule.block;

import com.curiats.returncapsule.entity.fall.CapsuleKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

/** The manned capsule is a separate block: 5x5 footprint, six slots, its own block entity type. */
public class MannedCapsuleBlock extends CapsuleBlock {

    public MannedCapsuleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected CapsuleKind defaultKind() {
        return CapsuleKind.MANNED;
    }

    /** Jade and every other name lookup goes through this; never inherit the supply capsule's name. */
    @Override
    public String getDescriptionId() {
        return "block.returncapsule.manned_capsule";
    }

    @Override
    public net.minecraft.network.chat.MutableComponent getName() {
        return Component.translatable(this.getDescriptionId());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MannedCapsuleBlockEntity(pos, state);
    }
}
