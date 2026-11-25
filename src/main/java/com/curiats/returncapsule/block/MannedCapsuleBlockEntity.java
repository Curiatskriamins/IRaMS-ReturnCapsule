package com.curiats.returncapsule.block;

import com.curiats.returncapsule.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

public class MannedCapsuleBlockEntity extends CapsuleBlockEntity {

    public MannedCapsuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MANNED_CAPSULE.get(), pos, state, true);
    }

    @Override
    public Component getName() {
        return Component.translatable("block.returncapsule.manned_capsule");
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }
}
