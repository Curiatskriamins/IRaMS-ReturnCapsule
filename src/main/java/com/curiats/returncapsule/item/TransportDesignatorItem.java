package com.curiats.returncapsule.item;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.block.TransportMarkerBlockEntity;
import com.curiats.returncapsule.compat.BeaconRecords;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

public class TransportDesignatorItem extends Item {
    private static final String TAG_CHARGE = "Charge";
    private static final String TAG_MARKER_POS = "MarkerPos";
    private static final String TAG_MARKER_TOKEN = "MarkerToken";
    private static final String TAG_CONFIRM_UNTIL = "ConfirmUntil";
    private static final String TAG_LEGACY_DAMAGE = "Damage";

    public TransportDesignatorItem(Properties properties) {
        super(properties);
    }

    public static int charge(ItemStack stack) {
        int max = Config.designatorMaxCharge();
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return max;
        }
        if (tag.contains(TAG_CHARGE)) {
            return Mth.clamp(tag.getInt(TAG_CHARGE), 0, max);
        }
        return Math.max(0, max - Math.max(0, tag.getInt(TAG_LEGACY_DAMAGE)));
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.getOrCreateTag().putInt(TAG_CHARGE, Mth.clamp(charge, 0, Config.designatorMaxCharge()));
    }

    private static int migrateCharge(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_CHARGE) && tag.contains(TAG_LEGACY_DAMAGE)) {
            setCharge(stack, Math.max(0, Config.designatorMaxCharge() - Math.max(0, tag.getInt(TAG_LEGACY_DAMAGE))));
            tag.remove(TAG_LEGACY_DAMAGE);
        }
        return charge(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return charge(stack) < Config.designatorMaxCharge();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = Config.designatorMaxCharge();
        return Math.round(13.0F * charge(stack) / max);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = (float) charge(stack) / Config.designatorMaxCharge();
        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            clearMarker(level, stack);
            return InteractionResult.CONSUME;
        }

        BlockPos platform = findPlatform(level, context.getClickedPos());
        if (platform == null) {
            return InteractionResult.FAIL;
        }
        BlockPos markerPos = platform.above();

        TransportCapsuleEntity inFlight = TransportCapsuleEntity.find(level, markerPos);
        if (inFlight != null) {
            if (inFlight.addPassenger(player)) {
                player.getCooldowns().addCooldown(this, Config.DESIGNATOR_COOLDOWN_TICKS.get());
                return InteractionResult.CONSUME;
            }
            return InteractionResult.FAIL;
        }

        if (!(level.getBlockEntity(markerPos) instanceof TransportMarkerBlockEntity marker)
                || !markerPos.equals(getMarkerPos(stack))) {
            if (!level.getBlockState(markerPos).isAir()) {
                return InteractionResult.FAIL;
            }
            UUID token = UUID.randomUUID();
            level.setBlock(markerPos, ModBlocks.TRANSPORT_MARKER.get().defaultBlockState(), 3);
            if (level.getBlockEntity(markerPos) instanceof TransportMarkerBlockEntity created) {
                created.configure(player.getUUID(), token, platform);
            }
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong(TAG_MARKER_POS, markerPos.asLong());
            tag.putUUID(TAG_MARKER_TOKEN, token);
            tag.remove(TAG_CONFIRM_UNTIL);
            return InteractionResult.CONSUME;
        }

        CompoundTag tag = stack.getOrCreateTag();
        long now = level.getGameTime();
        long confirmUntil = tag.getLong(TAG_CONFIRM_UNTIL);
        if (now < confirmUntil) {
            int max = Config.designatorMaxCharge();
            if (migrateCharge(stack) < max) {
                return InteractionResult.FAIL;
            }
            if (!player.isCreative() && !canPay(player)) {
                return InteractionResult.FAIL;
            }
            if (TransportCapsuleEntity.spawn((ServerLevel) level, markerPos, player)) {
                if (!player.isCreative()) {
                    consumeNetherStar(player);
                }
                setCharge(stack, Math.min(1, max - 1));
                tag.remove(TAG_CONFIRM_UNTIL);
                player.getCooldowns().addCooldown(this, Config.DESIGNATOR_COOLDOWN_TICKS.get());
                return InteractionResult.CONSUME;
            }
            return InteractionResult.FAIL;
        }

        tag.putLong(TAG_CONFIRM_UNTIL, now + Config.DESIGNATOR_CONFIRM_TICKS.get());
        return InteractionResult.CONSUME;
    }

    private static boolean canPay(ServerPlayer player) {
        return player.getServer() != null
                && BeaconRecords.get(player.getServer()).hasActiveBeacon(player.getServer(), player.getUUID())
                && hasNetherStar(player);
    }

    private static boolean hasNetherStar(ServerPlayer player) {
        for (int i = 0, size = player.getInventory().getContainerSize(); i < size; i++) {
            if (player.getInventory().getItem(i).is(Items.NETHER_STAR)) {
                return true;
            }
        }
        return false;
    }

    private static void consumeNetherStar(ServerPlayer player) {
        player.getInventory().clearOrCountMatchingItems(stack -> stack.is(Items.NETHER_STAR),
                1, player.getInventory());
    }

    private static BlockPos getMarkerPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_MARKER_POS) ? BlockPos.of(tag.getLong(TAG_MARKER_POS)) : null;
    }

    private static void clearMarker(Level level, ItemStack stack) {
        BlockPos pos = getMarkerPos(stack);
        if (pos != null && level.getBlockState(pos).is(ModBlocks.TRANSPORT_MARKER.get())) {
            level.removeBlock(pos, false);
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(TAG_MARKER_POS);
        tag.remove(TAG_MARKER_TOKEN);
        tag.remove(TAG_CONFIRM_UNTIL);
    }

    public static BlockPos findPlatform(Level level, BlockPos clicked) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = Config.PLATFORM_SEARCH_RADIUS.get();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos center = clicked.offset(dx, 0, dz);
                double distance = center.distSqr(clicked);
                if (distance >= bestDistance) {
                    continue;
                }
                if (!isCompletePlatform(level, center)) {
                    continue;
                }
                bestDistance = distance;
                best = center.immutable();
                if (distance == 0.0D) {
                    return best;
                }
            }
        }
        return best;
    }

    public static boolean isCompletePlatform(Level level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getBlockState(center.offset(dx, 0, dz)).is(Blocks.OBSIDIAN)) {
                    return false;
                }
            }
        }
        return level.getBlockState(center.above()).isAir()
                || level.getBlockState(center.above()).is(ModBlocks.TRANSPORT_MARKER.get());
    }
}
