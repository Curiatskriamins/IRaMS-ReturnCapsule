package com.curiats.returncapsule.compat.tacz;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the item stacks TaCZ expects. Both the capsule loot filler and the humanoid weapon supply need
 * the same NBT layout, so it lives here once.
 */
final class TaczItems {
    static final ResourceLocation GUN_ITEM = new ResourceLocation("tacz", "modern_kinetic_gun");
    static final ResourceLocation ATTACHMENT_ITEM = new ResourceLocation("tacz", "attachment");
    /** Chance that any one allowed slot actually gets an attachment, so guns are not all fully kitted. */
    private static final float ATTACHMENT_CHANCE = 0.65F;
    private static final String ATTACHMENT_TYPE_CLASS = TaczApi.ATTACHMENT_TYPE_CLASS;
    static final ResourceLocation AMMO_ITEM = new ResourceLocation("tacz", "ammo");

    static ItemStack gun(GunInfo info, int magazine) {
        return gun(info, magazine, 0);
    }

    /** Mobs get dummy ammo so TaCZ never asks them for an inventory to reload from. */
    static ItemStack mobGun(GunInfo info, int magazine, int dummyAmmo) {
        return gun(info, magazine, dummyAmmo);
    }

    private static ItemStack gun(GunInfo info, int magazine, int dummyAmmo) {
        Item item = BuiltInRegistries.ITEM.get(GUN_ITEM);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("GunId", info.id().toString());
        tag.putString("GunFireMode", info.fireMode());
        tag.putInt("GunCurrentAmmoCount", Math.max(0, magazine));
        tag.putBoolean("HasBulletInBarrel", magazine > 0);
        if (dummyAmmo > 0) {
            tag.putInt("DummyAmmo", dummyAmmo);
            tag.putInt("MaxDummyAmmo", dummyAmmo);
        }
        return stack;
    }

    static ItemStack attachment(ResourceLocation attachmentId) {
        Item item = BuiltInRegistries.ITEM.get(ATTACHMENT_ITEM);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("AttachmentId", attachmentId.toString());
        return stack;
    }

    /**
     * Fits a random selection of attachments to a gun, using TaCZ's own compatibility rules.
     * <p>
     * Every candidate is filtered through {@code IGun.allowAttachmentType} first — that is the rule that a slot the gun
     * disables cannot be used at all — and then through {@code IGun.allowAttachment} for the specific item, before
     * {@code IGun.installAttachment} writes it. Nothing is forced: an incompatible attachment is simply skipped, so a
     * gun can end up with fewer fittings than it has slots.
     * <p>
     * Attachments are drawn from the same pack as the gun, which is what makes the result look like a coherent build
     * rather than a random assortment, and each slot has a chance to stay empty.
     */
    static void randomizeAttachments(ItemStack gun, RandomSource random) {
        if (gun.isEmpty() || !TaczApi.available()) {
            return;
        }
        try {
            Object gunHandle = TaczApi.gunOrNull(gun);
            if (gunHandle == null) {
                return;
            }
            ResourceLocation gunId = TaczRifles.gunId(gun);
            if (gunId == null) {
                return;
            }
            Object[] types = Class.forName(ATTACHMENT_TYPE_CLASS).getEnumConstants();
            for (Object type : types) {
                if (!TaczApi.allowAttachmentType(gunHandle, type)) {
                    continue;   // slot disabled for this gun
                }
                if (random.nextFloat() > ATTACHMENT_CHANCE) {
                    continue;   // this slot stays empty
                }
                ItemStack candidate = pickAttachment(gunHandle, type, gunId.getNamespace(), random);
                if (!candidate.isEmpty()) {
                    TaczApi.installAttachment(gunHandle, gun, candidate);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("Could not fit attachments: {}", exception.toString());
        }
    }

    /** Random attachment of the requested slot from the same pack that the gun actually accepts. */
    private static ItemStack pickAttachment(Object gunHandle, Object type, String pack, RandomSource random)
            throws ReflectiveOperationException {
        List<ResourceLocation> pool = TaczCatalog.attachmentsOfPack(pack);
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<ResourceLocation> shuffled = new ArrayList<>(pool);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            ResourceLocation swap = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, swap);
        }
        for (ResourceLocation id : shuffled) {
            ItemStack stack = attachment(id);
            if (stack.isEmpty() || !type.equals(TaczApi.attachmentType(stack))) {
                continue;
            }
            if (TaczApi.allowAttachment(gunHandle, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static ItemStack ammo(ResourceLocation ammoId, int count) {
        if (ammoId == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(AMMO_ITEM);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("AmmoId", ammoId.toString());
        stack.setCount(Math.min(count, Math.max(1, stack.getMaxStackSize())));
        return stack;
    }

    private TaczItems() {
    }
}
