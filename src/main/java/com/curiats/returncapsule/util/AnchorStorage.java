package com.curiats.returncapsule.util;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.item.StandardIssueSwordItem;
import com.curiats.returncapsule.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Humanoid storage that lives on the anchor item itself, three per anchor.
 *
 * <p>Kept separate from the player so several anchors hold their own cargo; the release cooldown is per anchor too.</p>
 */
public final class AnchorStorage {

    public static final int CAPACITY = 3;

    public static final int RELEASE_COOLDOWN_TICKS = 600;

    private static final String TAG_COUNT = "AnchorCount";

    private static final String TAG_GUNS = "AnchorGuns";

    private static final String TAG_READY = "AnchorReady";

    /** Read only: the item property runs on the client every frame, so it must not create a tag. */
    public static int count(ItemStack anchor) {
        return anchor.hasTag() ? anchor.getTag().getInt(TAG_COUNT) : 0;
    }

    /** Humanoids held in any anchor in the player's inventory; they occupy the three owned slots. */
    public static int storedTotal(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(com.curiats.returncapsule.registry.ModItems.STANDARD_STORAGE_ANCHOR.get())) {
                total += count(stack);
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(com.curiats.returncapsule.registry.ModItems.STANDARD_STORAGE_ANCHOR.get())) {
            total += count(offhand);
        }
        return total;
    }

    public static boolean store(ServerPlayer player, ItemStack anchor, NeutralHumanoid humanoid) {
        CompoundTag data = anchor.getOrCreateTag();
        if (data.getInt(TAG_COUNT) >= CAPACITY || humanoid.isRemoved() || humanoid.isPassenger()) {
            ReturnCapsuleMod.LOGGER.info("[anchor] store refused: count={} removed={} passenger={}",
                    data.getInt(TAG_COUNT), humanoid.isRemoved(), humanoid.isPassenger());
            return false;
        }
        ListTag guns = data.getList(TAG_GUNS, Tag.TAG_COMPOUND);
        take(guns, humanoid.getItemInHand(InteractionHand.MAIN_HAND));
        take(guns, humanoid.getItemInHand(InteractionHand.OFF_HAND));
        int before = data.getInt(TAG_COUNT);
        data.putInt(TAG_COUNT, before + 1);
        data.put(TAG_GUNS, guns);
        if (!humanoid.removeForStorage()) {
            data.putInt(TAG_COUNT, before);
            ReturnCapsuleMod.LOGGER.info("[anchor] store refused: humanoid removal was blocked");
            return false;
        }
        ReturnCapsuleMod.LOGGER.info("[anchor] stored humanoid, count={}", data.getInt(TAG_COUNT));
        resync(player);
        return true;
    }

    public static boolean canRelease(ItemStack anchor, long gameTime) {
        return count(anchor) > 0 && gameTime >= anchor.getOrCreateTag().getLong(TAG_READY);
    }

    public static long readyAt(ItemStack anchor) {
        return anchor.getOrCreateTag().getLong(TAG_READY);
    }

    public static boolean release(ServerPlayer player, ItemStack anchor, Vec3 position, long gameTime) {
        if (!canRelease(anchor, gameTime) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        NeutralHumanoid humanoid = ModEntities.BASE_HUMANOID.get().create(level);
        if (humanoid == null) {
            return false;
        }
        humanoid.moveTo(position.x, position.y, position.z, level.random.nextFloat() * 360.0F, 0.0F);
        humanoid.setHealth(humanoid.getMaxHealth());
        humanoid.setPersistenceRequired();
        humanoid.setOwnerUUID(player.getUUID());
        humanoid.setItemInHand(InteractionHand.MAIN_HAND, StandardIssueSwordItem.createStack());
        CompoundTag data = anchor.getOrCreateTag();
        ListTag guns = data.getList(TAG_GUNS, Tag.TAG_COMPOUND);
        if (!guns.isEmpty()) {
            int index = level.random.nextInt(guns.size());
            humanoid.setItemInHand(InteractionHand.OFF_HAND, ItemStack.of(guns.getCompound(index)));
            guns.remove(index);
            data.put(TAG_GUNS, guns);
        }
        data.putInt(TAG_COUNT, Math.max(0, data.getInt(TAG_COUNT) - 1));
        data.putLong(TAG_READY, gameTime + RELEASE_COOLDOWN_TICKS);
        level.addFreshEntity(humanoid);
        resync(player);
        return true;
    }

    /** The stack lives in the inventory, so the client needs a fresh copy to repaint the stored-count icon. */
    private static void resync(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static void take(ListTag guns, ItemStack stack) {
        if (!stack.isEmpty()) {
            guns.add(stack.save(new CompoundTag()));
        }
    }

    private AnchorStorage() {
    }
}
