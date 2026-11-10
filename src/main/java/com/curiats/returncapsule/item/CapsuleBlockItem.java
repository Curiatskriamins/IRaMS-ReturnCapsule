package com.curiats.returncapsule.item;

import com.curiats.returncapsule.block.CapsuleStructure;
import com.curiats.returncapsule.registry.ModBlocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;

public class CapsuleBlockItem extends BlockItem {
    public CapsuleBlockItem(Properties properties) {
        super(ModBlocks.CAPSULE.get(), properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (CapsuleStructure.isCapsuleStructure(context.getLevel(), context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }
}
