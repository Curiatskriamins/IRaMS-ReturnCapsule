package com.curiats.returncapsule.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class CapsuleInteraction {
    public static InteractionResult use(Level level, BlockPos pos, Player player,
                                        InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CapsuleBlockEntity capsule)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                CapsuleBlockEntity.AnimState state = capsule.getAnimState();
                if (state == CapsuleBlockEntity.AnimState.OPEN || state == CapsuleBlockEntity.AnimState.OPENING) {
                    capsule.startClosing();
                    return InteractionResult.CONSUME;
                }
                return InteractionResult.PASS;
            }
            switch (capsule.getAnimState()) {
                case CLOSED -> capsule.startOpening();
                case OPEN -> player.openMenu(capsule);
                case OPENING, CLOSING -> {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    private CapsuleInteraction() {
    }
}
