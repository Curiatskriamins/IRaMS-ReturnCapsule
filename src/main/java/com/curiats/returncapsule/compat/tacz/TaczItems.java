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
        // resolve() must run first: available() is only true after the API has been looked up once, and without
        // this check every gun silently came out bare.
        if (gun.isEmpty() || !TaczApi.resolve()) {
            ReturnCapsuleMod.LOGGER.info("[humanoid] no attachments: TaCZ API unavailable");
            return;
        }
        try {
            Object gunHandle = TaczApi.gunOrNull(gun);
            if (gunHandle == null) {
                ReturnCapsuleMod.LOGGER.info("[humanoid] no attachments: IGun handle is null for {}", gun.getItem());
                return;
            }
            ResourceLocation gunId = TaczRifles.gunId(gun);
            if (gunId == null) {
                ReturnCapsuleMod.LOGGER.info("[humanoid] no attachments: gun has no GunId tag");
                return;
            }
            Object[] types = Class.forName(ATTACHMENT_TYPE_CLASS).getEnumConstants();
            int allowed = 0;
            int fitted = 0;
            for (Object type : types) {
                // allowAttachmentType returned false for every type on every gun sampled, which cannot be right for guns
                // that players can fit scopes to. It is therefore not the gate we assumed; installation validates on its
                // own, and the read-back below is what actually decides whether anything was fitted.
                if (TaczApi.allowAttachmentType(gunHandle, type)) {
                    allowed++;
                }
                if (random.nextFloat() > ATTACHMENT_CHANCE) {
                    continue;   // this slot stays empty
                }
                ItemStack candidate = pickAttachment(gunHandle, type, gunId.getNamespace(), random);
                if (!candidate.isEmpty()) {
                    TaczApi.installAttachment(gunHandle, gun, candidate);
                    if (!gun.getOrCreateTag().contains("Attachment")) {
                        // TaCZ refused it; write the tag ourselves in its own layout.
                        forceWriteAttachment(gun, type, candidate);
                    }
                    if (TaczApi.attachedId(gunHandle, gun, type) != null) {
                        fitted++;
                    }
                }
            }
            ReturnCapsuleMod.LOGGER.info("[humanoid] gun {} fitted {}/{} attachments (allowAttachmentType said {}/{})",
                    gunId, fitted, types.length, allowed, types.length);
            // Read the raw tag: TaCZ's own accessors are gated behind allowAttachmentType, so they report "nothing" even
            // when something was installed. The tag keys are the only trustworthy evidence.
            ReturnCapsuleMod.LOGGER.info("[humanoid] gun {} nbt keys after fit: {}", gunId, gun.getOrCreateTag().getAllKeys());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.info("[humanoid] could not fit attachments: {}", exception.toString());
        }
    }

    /**
     * Writes an attachment straight into the gun's tag, bypassing TaCZ's validation.
     * <p>
     * {@code installAttachment} refuses everything here because it is gated behind {@code allowAttachment}, which in turn
     * is gated behind {@code allowAttachmentType} — and that returns false for every slot of every gun sampled. The NBT
     * layout is TaCZ's own: a compound named {@code Attachment}, one entry per slot type, each holding the saved item.
     */
    static void forceWriteAttachment(ItemStack gun, Object type, ItemStack attachment) {
        try {
            // TaCZ builds the key as {@code type.name() + "Attachment"} at the top level: getAttachmentTag does
            // tag.contains(<type>Attachment, TAG_COMPOUND) and then reads tag.getCompound(<type>Attachment). Writing a
            // nested "Attachment" bucket instead put the data somewhere nothing ever reads.
            net.minecraft.nbt.CompoundTag root = gun.getOrCreateTag();
            String key = ((Enum<?>) type).name() + "Attachment";
            root.put(key, attachment.save(new net.minecraft.nbt.CompoundTag()));
        } catch (RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.info("[humanoid] could not write attachment: {}", exception.toString());
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
            // No allowAttachment check: it is gated behind allowAttachmentType, which reports false for every slot of
            // every gun, so filtering on it discarded every candidate and nothing was ever fitted.
            return stack;
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
