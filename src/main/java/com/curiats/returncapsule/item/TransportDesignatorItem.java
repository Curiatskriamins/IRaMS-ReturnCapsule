package com.curiats.returncapsule.item;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.block.TransportMarkerBlockEntity;
import com.curiats.returncapsule.compat.BeaconRecords;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.registry.ModBlocks;
import com.curiats.returncapsule.util.NetherStars;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

public class TransportDesignatorItem extends Item {
    private static final String TAG_CHARGE = "Charge";
    private static final String TAG_MARKER_POS = "MarkerPos";
    private static final String TAG_MARKER_TOKEN = "MarkerToken";
    private static final String TAG_MANNED_SUMMONS = "returncapsule_manned_summons";

    private static final int MAX_MANNED_SUMMONS = 3;

    private static final int MAX_OWNED_HUMANOIDS = 3;

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
        BlockPos platform = findPlatform(level, context.getClickedPos());
        if (platform != null) {
            return rideFromPlatform(level, stack, player, platform);
        }
        if (player.isShiftKeyDown()) {
            // Sneaking on ordinary ground is the deliberate way to summon a manned capsule.
            return awayFromPlatform(level, stack, player);
        }
        if (getMarkerPos(stack) != null) {
            return returnToMarker(level, stack, player);
        }
        return fail(player, "message.returncapsule.designator_no_marker");
    }

    /** A complete 5x5 obsidian pad rides the player down to its centre, no portal required. */
    private static InteractionResult rideFromPlatform(Level level, ItemStack stack, ServerPlayer player,
                                                      BlockPos platform) {
        BlockPos landing = platform.above();
        TransportCapsuleEntity inFlight = TransportCapsuleEntity.find(level, landing);
        if (inFlight != null) {
            return inFlight.addPassenger(player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }

        CompoundTag tag = stack.getOrCreateTag();
        BlockPos marked = getMarkerPos(stack);
        boolean markedHere = marked != null && marked.equals(landing)
                && level.getBlockState(landing).is(ModBlocks.TRANSPORT_MARKER.get());
        if (!markedHere) {
            // First click only marks; the red beam is kept by the item token and the marker re-checks its 5x5 pad.
            UUID token = UUID.randomUUID();
            level.setBlock(landing, ModBlocks.TRANSPORT_MARKER.get().defaultBlockState(), 3);
            if (level.getBlockEntity(landing) instanceof TransportMarkerBlockEntity created) {
                created.configure(player.getUUID(), token, platform);
            }
            tag.putLong(TAG_MARKER_POS, landing.asLong());
            tag.putUUID(TAG_MARKER_TOKEN, token);
            tag.remove(TAG_CONFIRM_UNTIL);
            return InteractionResult.CONSUME;
        }
        int max = Config.designatorMaxCharge();
        if (!player.isCreative() && migrateCharge(stack) < max) {
            return failCharge(player, stack);
        }
        if (!player.isCreative() && !canPay(player)) {
            return fail(player, "message.returncapsule.designator_payment");
        }
        if (!(level instanceof ServerLevel server) || !TransportCapsuleEntity.spawn(server, landing, player)) {
            return fail(player, "message.returncapsule.designator_spawn");
        }
        if (!player.isCreative()) {
            NetherStars.consumeOne(player);
        }
        setCharge(stack, 1);
        player.getCooldowns().addCooldown(stack.getItem(), Config.DESIGNATOR_COOLDOWN_TICKS.get());
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server) || level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (getMarkerPos(stack) == null) {
            return new InteractionResultHolder<>(
                    fail(server, "message.returncapsule.designator_no_marker"), stack);
        }
        return new InteractionResultHolder<>(returnToMarker(level, stack, server), stack);
    }

    /** Not on an obsidian platform: summon a manned capsule, or fall back to riding home once used up. */
    private static InteractionResult awayFromPlatform(Level level, ItemStack stack, ServerPlayer player) {
        int summons = player.getPersistentData().getInt(TAG_MANNED_SUMMONS);
        // Stored humanoids and living ones share the same three slot cap.
        int owned = com.curiats.returncapsule.event.HumanoidOwnerEvents.ownedCount(
                player.serverLevel(), player.getUUID())
                + com.curiats.returncapsule.util.AnchorStorage.storedTotal(player);
        if (summons >= MAX_MANNED_SUMMONS || owned >= MAX_OWNED_HUMANOIDS) {
            return returnToMarker(level, stack, player);
        }
        if (!player.isCreative() && migrateCharge(stack) < Config.designatorMaxCharge()) {
            return failCharge(player, stack);
        }
        if (!player.isCreative() && !NetherStars.has(player)) {
            return fail(player, "message.returncapsule.designator_star");
        }
        BlockPos landing = player.blockPosition().above(2);
        if (!(level instanceof ServerLevel server)
                || !TransportCapsuleEntity.spawnManned(server, landing, player)) {
            return fail(player, "message.returncapsule.designator_spawn");
        }
        if (!player.isCreative()) {
            NetherStars.consumeOne(player);
        }
        setCharge(stack, 1);
        player.getPersistentData().putInt(TAG_MANNED_SUMMONS, summons + 1);
        player.getCooldowns().addCooldown(stack.getItem(), Config.DESIGNATOR_COOLDOWN_TICKS.get());
        return InteractionResult.CONSUME;
    }

    private static InteractionResult returnToMarker(Level level, ItemStack stack, ServerPlayer player) {
        BlockPos marker = getMarkerPos(stack);
        if (!(level instanceof ServerLevel server) || marker == null) {
            return fail(player, "message.returncapsule.designator_no_marker");
        }
        // The portal may be gone, so the return trip loads the recorded marker chunk itself; the flight ticket keeps
        // it ticking until touchdown.
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(marker);
        server.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL, chunk, 2, marker);
        server.getChunk(chunk.x, chunk.z);
        TransportCapsuleEntity inFlight = TransportCapsuleEntity.find(level, marker);
        if (inFlight != null) {
            return inFlight.addPassenger(player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        return TransportCapsuleEntity.spawn(server, marker, player)
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    private static InteractionResult fail(ServerPlayer player, String key) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
        return InteractionResult.FAIL;
    }

    private static InteractionResult failCharge(ServerPlayer player, ItemStack stack) {
        int max = Config.designatorMaxCharge();
        int current = migrateCharge(stack);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.returncapsule.designator_charge", current * 100 / max), true);
        return InteractionResult.FAIL;
    }

    private static boolean canPay(ServerPlayer player) {
        return player.getServer() != null
                && BeaconRecords.get(player.getServer()).hasActiveBeacon(player.getServer(), player.getUUID())
                && NetherStars.has(player);
    }

    public static boolean hasNetherPortal(Level level, BlockPos center) {
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk =
                        level.getChunk(chunkX + dx, chunkZ + dz);
                for (net.minecraft.world.level.chunk.LevelChunkSection section : chunk.getSections()) {
                    if (section.maybeHas(state ->
                            state.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static BlockPos markerPos(ItemStack stack) {
        return getMarkerPos(stack);
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
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!level.getBlockState(center.offset(dx, 0, dz)).is(Blocks.OBSIDIAN)) {
                    return false;
                }
            }
        }
        return true;
    }
}
