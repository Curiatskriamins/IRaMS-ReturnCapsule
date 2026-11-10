package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.block.CapsuleBlockEntity;
import com.curiats.returncapsule.block.CapsuleStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.BlockHitResult;

public final class PearlBoarding {
    public static boolean tryBoard(ThrownEnderpearl pearl, BlockHitResult hit) {
        if (pearl.level().isClientSide || !(pearl.getOwner() instanceof ServerPlayer player)) {
            return false;
        }
        BlockPos main = CapsuleStructure.findMain(pearl.level(), hit.getBlockPos());
        if (main == null || !(pearl.level().getBlockEntity(main) instanceof CapsuleBlockEntity capsule)) {
            return false;
        }
        if (!capsule.isOpenOrClosing()) {
            return false;
        }
        if (!capsule.addHidden(player)) {
            return false;
        }
        pearl.discard();
        return true;
    }

    private PearlBoarding() {
    }
}
