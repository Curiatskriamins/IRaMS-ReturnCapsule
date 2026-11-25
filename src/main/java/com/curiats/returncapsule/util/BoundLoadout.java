package com.curiats.returncapsule.util;

import com.curiats.returncapsule.item.StandardIssueSwordItem;
import com.curiats.returncapsule.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The standard issue set handed out once per player by the manned return capsule.
 *
 * <p>The five pieces are bound to the player who first summoned it: they carry the owner's UUID, nothing else may pick
 * them up, they never drop on death, and the four armour pieces are put back into their slots whenever something other
 * than the player removes or destroys them. The sword is an ordinary bound item in the player's inventory; only the
 * armour is locked to a slot.</p>
 */
public final class BoundLoadout {

    public static final int SIZE = 5;

    private static final String TAG_STACKS = "returncapsule_loadout_stacks";

    private static final String TAG_GRANTED = "returncapsule_loadout_granted";

    private static final String TAG_OPENED = "returncapsule_loadout_opened";

    public static final String TAG_BOUND = "returncapsuleLoadoutOwner";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private BoundLoadout() {
    }

    public static boolean granted(ServerPlayer player) {
        return player.getPersistentData().getBoolean(TAG_GRANTED);
    }

    public static boolean opened(ServerPlayer player) {
        return player.getPersistentData().getBoolean(TAG_OPENED);
    }

    /** Builds the five bound pieces; returns null when the player already got their one allocation. */
    @Nullable
    public static boolean isGranted(net.minecraft.world.entity.player.Player player) {
        return player.getPersistentData().getBoolean(TAG_GRANTED);
    }

    /** Writes the granted set and the storage anchor into the capsule container. */
    public static boolean fillCapsule(com.curiats.returncapsule.block.CapsuleBlockEntity capsule,
                                      net.minecraft.server.level.ServerPlayer player) {
        ItemStack[] set = grant(player);
        if (set == null) {
            return false;
        }
        for (int slot = 0; slot < set.length; slot++) {
            capsule.setItem(slot, set[slot]);
        }
        capsule.setItem(set.length,
                new ItemStack(com.curiats.returncapsule.registry.ModItems.STANDARD_STORAGE_ANCHOR.get()));
        return true;
    }

    public static ItemStack[] grant(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(TAG_GRANTED)) {
            return null;
        }
        ItemStack[] pieces = freshSet();
        if (pieces == null) {
            return null;
        }
        ListTag list = new ListTag();
        for (ItemStack piece : pieces) {
            list.add(bind(piece, player).save(new CompoundTag()));
        }
        data.put(TAG_STACKS, list);
        data.putBoolean(TAG_GRANTED, true);
        return pieces;
    }

    @Nullable
    private static ItemStack[] freshSet() {
        ItemStack helmet = new ItemStack(ModItems.BASE_HUMANOID_HELMET.get());
        ItemStack chest = new ItemStack(ModItems.BASE_HUMANOID_CHESTPLATE.get());
        ItemStack legs = new ItemStack(ModItems.BASE_HUMANOID_LEGGINGS.get());
        ItemStack boots = new ItemStack(ModItems.BASE_HUMANOID_BOOTS.get());
        ItemStack sword = StandardIssueSwordItem.createStack();
        return new ItemStack[]{helmet, chest, legs, boots, sword};
    }

    @Nullable
    public static ItemStack piece(ServerPlayer player, int index) {
        ListTag list = player.getPersistentData().getList(TAG_STACKS, Tag.TAG_COMPOUND);
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return ItemStack.of(list.getCompound(index));
    }

    public static ItemStack bind(ItemStack stack, ServerPlayer player) {
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putUUID(TAG_BOUND, player.getUUID());
        }
        return stack;
    }

    public static boolean isBound(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().hasUUID(TAG_BOUND);
    }

    @Nullable
    public static UUID boundOwner(ItemStack stack) {
        if (!isBound(stack)) {
            return null;
        }
        return stack.getTag().getUUID(TAG_BOUND);
    }

    public static boolean isBoundTo(ItemStack stack, ServerPlayer player) {
        UUID owner = boundOwner(stack);
        return owner != null && owner.equals(player.getUUID());
    }

    /**
     * Puts the four armour pieces back into their slots when something removed or destroyed them.
     *
     * <p>Called from equipment change, drops, respawn and a low frequency safety net rather than every tick. The piece
     * that occupied the slot is dropped rather than deleted.</p>
     */
    public static void restoreArmor(ServerPlayer player) {
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            EquipmentSlot slot = ARMOR_SLOTS[index];
            ItemStack current = player.getItemBySlot(slot);
            if (isBoundTo(current, player)) {
                continue;
            }
            ItemStack wanted = piece(player, index);
            if (wanted == null || wanted.isEmpty()) {
                continue;
            }
            ItemStack restore = bind(wanted.copy(), player);
            if (!current.isEmpty()) {
                player.drop(current.copy(), false);
            }
            player.setItemSlot(slot, restore);
        }
    }

    /**
     * Puts the bound sword back when it is gone entirely (death, destruction), but only when the player has no dropped
     * copy in the world, so a deliberate drop is not duplicated.
     */
    public static void restoreSword(ServerPlayer player) {
        if (!granted(player)) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isBoundTo(player.getInventory().getItem(slot), player)) {
                ItemStack held = player.getInventory().getItem(slot);
                if (held.is(com.curiats.returncapsule.registry.ModItems.STANDARD_ISSUE_SWORD.get())) {
                    return;
                }
            }
        }
        ItemStack wanted = piece(player, SIZE - 1);
        if (wanted == null || wanted.isEmpty()) {
            return;
        }
        ItemStack sword = bind(wanted.copy(), player);
        if (!player.getInventory().add(sword)) {
            player.drop(sword, false);
        }
    }

}
