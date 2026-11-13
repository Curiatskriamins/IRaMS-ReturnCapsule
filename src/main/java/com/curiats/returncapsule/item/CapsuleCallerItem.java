package com.curiats.returncapsule.item;

import com.curiats.returncapsule.block.CapsuleStructure;
import com.curiats.returncapsule.client.ClientTooltip;
import com.curiats.returncapsule.entity.SupplyCapsuleEntity;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.progress.TierResolver;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

public class CapsuleCallerItem extends Item {
    private static final String TAG_NETHER = "NetherEntered";
    private static final String TAG_STAR = "StarSeen";
    private static final String TAG_TIER_THREE = "TierThreeDone";
    private static final String TAG_WARNING = "returncapsule_caller_warning";
    private static final String TAG_TIER_THREE_DONE = "returncapsule_tier3_done";
    private static final ResourceLocation NETHER_ADVANCEMENT =
            new ResourceLocation("minecraft", "story/enter_the_nether");

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
        if (tier == SummonTier.ELITE) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.is(Items.NETHER_STAR)) {
                offhand.shrink(1);
                player.getPersistentData().putBoolean(TAG_TIER_THREE_DONE, true);
            } else if (hasNetherStar(player)) {
                if (!player.getPersistentData().getBoolean(TAG_WARNING)) {
                    player.getPersistentData().putBoolean(TAG_WARNING, true);
                    player.displayClientMessage(
                            Component.translatable("item.returncapsule.capsule_caller.warning"), false);
                    return InteractionResult.FAIL;
                }
                consumeNetherStar(player);
                player.getPersistentData().putBoolean(TAG_TIER_THREE_DONE, true);
            } else {
                return InteractionResult.FAIL;
            }
        }

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
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.getBoolean(TAG_NETHER) && hasEnteredNether(player)) {
            tag.putBoolean(TAG_NETHER, true);
        }
        if (!tag.getBoolean(TAG_STAR) && hasNetherStar(player)) {
            tag.putBoolean(TAG_STAR, true);
        }
        if (!tag.getBoolean(TAG_TIER_THREE) && player.getPersistentData().getBoolean(TAG_TIER_THREE_DONE)) {
            tag.putBoolean(TAG_TIER_THREE, true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getOrCreateTag();
        Boolean shift = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> ClientTooltip::isShiftDown);
        if (tag.getBoolean(TAG_TIER_THREE) && !Boolean.TRUE.equals(shift)) {
            tooltip.add(Component.translatable("item.returncapsule.capsule_caller.tooltip.shift"));
            return;
        }
        if (tag.getBoolean(TAG_NETHER)) {
            tooltip.add(Component.translatable("item.returncapsule.capsule_caller.tooltip.progress"));
        }
        if (tag.getBoolean(TAG_STAR)) {
            tooltip.add(Component.translatable("item.returncapsule.capsule_caller.tooltip.beacon"));
        }
    }

    private static boolean hasEnteredNether(ServerPlayer player) {
        if (player.getServer() == null) {
            return false;
        }
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(NETHER_ADVANCEMENT);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static boolean hasNetherStar(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.NETHER_STAR)) {
                return true;
            }
        }
        return player.getOffhandItem().is(Items.NETHER_STAR);
    }

    private static void consumeNetherStar(Player player) {
        player.getInventory().clearOrCountMatchingItems(
                stack -> stack.is(Items.NETHER_STAR), 1, player.getInventory());
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
