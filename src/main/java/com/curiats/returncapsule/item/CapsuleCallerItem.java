package com.curiats.returncapsule.item;

import com.curiats.returncapsule.block.CapsuleStructure;
import com.curiats.returncapsule.entity.SupplyCapsuleEntity;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.progress.TierResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public class CapsuleCallerItem extends Item {
    public CapsuleCallerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        
        if (CapsuleStructure.isCapsuleStructure(level, context.getClickedPos())) {
            return InteractionResult.PASS;
        }

        BlockPos anchor = findAnchor(level, context.getClickedPos(), context.getClickedFace());
        if (anchor == null) {
            return InteractionResult.FAIL;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        SummonTier tier = TierResolver.resolve((ServerLevel) level, anchor, player);
        SupplyCapsuleEntity capsule = new SupplyCapsuleEntity(level, anchor, player, tier);
        if (!level.addFreshEntity(capsule)) {
            return InteractionResult.FAIL;
        }

        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.returncapsule.capsule_caller.tooltip.progress"));
        tooltip.add(Component.translatable("item.returncapsule.capsule_caller.tooltip.beacon"));
    }

    @Nullable
    private static BlockPos findAnchor(Level level, BlockPos clicked, net.minecraft.core.Direction face) {
        BlockPos pos = clicked.relative(face);
        int maxY = level.getMaxBuildHeight() - 3;
        for (int i = 0; i < 8; i++) {
            if (pos.getY() > maxY || pos.getY() < level.getMinBuildHeight()) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) {
                return pos.immutable();
            }
            pos = pos.above();
        }
        return null;
    }
}
