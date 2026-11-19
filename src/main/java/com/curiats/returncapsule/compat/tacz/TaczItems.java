package com.curiats.returncapsule.compat.tacz;

import net.minecraft.nbt.CompoundTag;
import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.registries.ForgeRegistries;

final class TaczItems {
    static final ResourceLocation GUN_ITEM = ResourceLocation.tryBuild("tacz", "modern_kinetic_gun");
    static final ResourceLocation ATTACHMENT_ITEM = ResourceLocation.tryBuild("tacz", "attachment");

    private static final float ATTACHMENT_CHANCE = 0.65F;
    private static final String ATTACHMENT_TYPE_CLASS = TaczApi.ATTACHMENT_TYPE_CLASS;
    static final ResourceLocation AMMO_ITEM = ResourceLocation.tryBuild("tacz", "ammo");

    static ItemStack gun(GunInfo info, int magazine) {
        return gun(info, magazine, 0);
    }

    static ItemStack mobGun(GunInfo info, int magazine, int dummyAmmo) {
        return gun(info, magazine, dummyAmmo);
    }

    private static ItemStack gun(GunInfo info, int magazine, int dummyAmmo) {
        Item item = ForgeRegistries.ITEMS.getValue(GUN_ITEM);
        if (item == null || item == Items.AIR) {
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
        Item item = ForgeRegistries.ITEMS.getValue(ATTACHMENT_ITEM);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("AttachmentId", attachmentId.toString());
        return stack;
    }

    static void randomizeAttachments(ItemStack gun, RandomSource random) {

        if (gun.isEmpty() || !TaczApi.resolve()) {
            ReturnCapsuleMod.LOGGER.debug("[humanoid] no attachments: TaCZ API unavailable");
            return;
        }
        try {
            Object gunHandle = TaczApi.gunOrNull(gun);
            if (gunHandle == null) {
                ReturnCapsuleMod.LOGGER.debug("[humanoid] no attachments: IGun handle is null for {}", gun.getItem());
                return;
            }
            ResourceLocation gunId = TaczRifles.gunId(gun);
            if (gunId == null) {
                ReturnCapsuleMod.LOGGER.debug("[humanoid] no attachments: gun has no GunId tag");
                return;
            }
            Object[] types = Class.forName(ATTACHMENT_TYPE_CLASS).getEnumConstants();
            int allowed = 0;
            int fitted = 0;
            for (Object type : types) {

                if (TaczApi.allowAttachmentType(gunHandle, type)) {
                    allowed++;
                }
                if (random.nextFloat() > ATTACHMENT_CHANCE) {
                    continue;
                }
                ItemStack candidate = pickAttachment(gunHandle, type, gunId.getNamespace(), random);
                if (!candidate.isEmpty()) {
                    TaczApi.installAttachment(gunHandle, gun, candidate);
                    if (!gun.getOrCreateTag().contains("Attachment")) {

                        forceWriteAttachment(gun, type, candidate);
                    }
                    if (TaczApi.attachedId(gunHandle, gun, type) != null) {
                        fitted++;
                    }
                }
            }
            ReturnCapsuleMod.LOGGER.debug("[humanoid] gun {} fitted {}/{} attachments (allowAttachmentType said {}/{})",
                    gunId, fitted, types.length, allowed, types.length);

            ReturnCapsuleMod.LOGGER.debug("[humanoid] gun {} nbt keys after fit: {}", gunId, gun.getOrCreateTag().getAllKeys());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("[humanoid] could not fit attachments: {}", exception.toString());
        }
    }

    static void forceWriteAttachment(ItemStack gun, Object type, ItemStack attachment) {
        try {

            net.minecraft.nbt.CompoundTag root = gun.getOrCreateTag();
            String key = ((Enum<?>) type).name() + "Attachment";
            root.put(key, attachment.save(new net.minecraft.nbt.CompoundTag()));
        } catch (RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("[humanoid] could not write attachment: {}", exception.toString());
        }
    }

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

            return stack;
        }
        return ItemStack.EMPTY;
    }

    static ItemStack ammo(ResourceLocation ammoId, int count) {
        if (ammoId == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(AMMO_ITEM);
        if (item == null || item == Items.AIR) {
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
