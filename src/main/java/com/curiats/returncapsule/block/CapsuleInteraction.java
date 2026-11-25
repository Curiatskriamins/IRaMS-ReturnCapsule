package com.curiats.returncapsule.block;

import com.curiats.returncapsule.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
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
                case CLOSED -> {
                    // Manned capsules open silently; the hiss is escaping air and happens once per capsule.
                    if (!capsule.manned() && capsule.claimPressureSound()) {
                        level.playSound(null, pos, ModSounds.CAPSULE_OPEN.get(),
                                SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    capsule.startOpening();
                }
                case OPEN -> {
                if (capsule.kitOwner() != null) {
                if (!(player instanceof net.minecraft.server.level.ServerPlayer server)
                        || !server.getUUID().equals(capsule.kitOwner())) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.returncapsule.kit_owner_only"), true);
                    return InteractionResult.CONSUME;
                }
                if (com.curiats.returncapsule.util.BoundLoadout.isGranted(server)) {
                    capsule.setKitOwner(null);
                } else if (com.curiats.returncapsule.util.BoundLoadout.fillCapsule(capsule, server)) {
                    capsule.setKitOwner(null);
                }
            }
            if (capsule.manned() && capsule.isEmptyContainer()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.returncapsule.manned_empty"), true);
                    return InteractionResult.CONSUME;
                }
                player.openMenu(capsule);
            }
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
