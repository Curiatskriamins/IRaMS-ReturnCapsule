package com.curiats.returncapsule.compat.tacz;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.compat.LrSupport;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TaczLootFiller {
    private static final ResourceLocation GUN_ITEM = new ResourceLocation("tacz", "modern_kinetic_gun");
    private static final ResourceLocation ATTACHMENT_ITEM = new ResourceLocation("tacz", "attachment");
    private static final ResourceLocation AMMO_ITEM = new ResourceLocation("tacz", "ammo");

    public static boolean fill(Container container, RandomSource random, SummonTier tier) {
        TaczCatalog.Snapshot catalog = TaczCatalog.snapshot();
        if (catalog.isEmpty()) {
            return false;
        }
        Map<String, List<GunInfo>> packs = catalog.gunsByPack();
        List<String> packNames = new ArrayList<>(packs.keySet());
        String pack = packNames.get(random.nextInt(packNames.size()));
        List<GunInfo> pool = new ArrayList<>(packs.get(pack));
        shuffle(pool, random);

        int minGuns = Config.TACZ_MIN_GUNS.get();
        int maxGuns = Math.max(minGuns, Config.TACZ_MAX_GUNS.get());
        int gunCount = Math.min(pool.size(), minGuns + random.nextInt(maxGuns - minGuns + 1));

        List<ResourceLocation> attachments = catalog.attachmentsByPack().getOrDefault(pack, List.of());
        ItemInserter.clear(container);
        ItemInserter inserter = new ItemInserter(container);

        for (int i = 0; i < gunCount; i++) {
            GunInfo gun = pool.get(i);
            inserter.insert(buildGun(gun));
            insertAttachments(inserter, attachments, random);
            addAmmo(inserter, gun.ammoId(), ammoCount(gun.type(), random, tier));
        }

        if (LrSupport.isLoaded()) {
            LrSupport.add(inserter, random);
        }
        container.setChanged();
        return true;
    }

    private static void insertAttachments(ItemInserter inserter, List<ResourceLocation> attachments,
                                          RandomSource random) {
        int available = attachments.size();
        if (available == 0) {
            return;
        }
        int min = Config.TACZ_MIN_ATTACHMENTS.get();
        int max = Math.max(min, Config.TACZ_MAX_ATTACHMENTS.get());
        int wanted = Math.min(available, min + random.nextInt(max - min + 1));
        if (wanted <= 0) {
            return;
        }
        if (wanted == available) {
            
            List<ResourceLocation> all = new ArrayList<>(attachments);
            shuffle(all, random);
            for (ResourceLocation id : all) {
                inserter.insert(buildAttachment(id));
            }
            return;
        }
        int[] picked = new int[wanted];
        int chosen = 0;
        while (chosen < wanted) {
            int index = random.nextInt(available);
            if (!contains(picked, chosen, index)) {
                picked[chosen++] = index;
            }
        }
        for (int index : picked) {
            inserter.insert(buildAttachment(attachments.get(index)));
        }
    }

    private static boolean contains(int[] values, int length, int value) {
        for (int i = 0; i < length; i++) {
            if (values[i] == value) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack buildGun(GunInfo gun) {
        Item item = BuiltInRegistries.ITEM.get(GUN_ITEM);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("GunId", gun.id().toString());
        tag.putString("GunFireMode", gun.fireMode());
        tag.putInt("GunCurrentAmmoCount", 0);
        tag.putBoolean("HasBulletInBarrel", false);
        return stack;
    }

    private static ItemStack buildAttachment(ResourceLocation attachmentId) {
        Item item = BuiltInRegistries.ITEM.get(ATTACHMENT_ITEM);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("AttachmentId", attachmentId.toString());
        return stack;
    }

    private static void addAmmo(ItemInserter inserter, ResourceLocation ammoId, int count) {
        if (ammoId == null || count <= 0) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(AMMO_ITEM);
        if (item == Items.AIR) {
            return;
        }
        ItemStack template = new ItemStack(item);
        template.getOrCreateTag().putString("AmmoId", ammoId.toString());
        int maxStack = Math.max(1, template.getMaxStackSize());
        int remaining = count;
        while (remaining > 0) {
            int stackCount = Math.min(maxStack, remaining);
            ItemStack stack = template.copy();
            stack.setCount(stackCount);
            if (!inserter.insert(stack)) {
                return;
            }
            remaining -= stackCount;
        }
    }

    private static int ammoCount(String type, RandomSource random, SummonTier tier) {
        int base = switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "sniper" -> 20 + random.nextInt(101);
            case "rifle" -> 120 + random.nextInt(408);
            case "pistol" -> 24 + random.nextInt(97);
            case "smg" -> 60 + random.nextInt(241);
            case "shotgun" -> 16 + random.nextInt(65);
            case "mg" -> 200 + random.nextInt(601);
            case "rpg" -> 2 + random.nextInt(7);
            default -> 60 + random.nextInt(121);
        };
        int tierFactor = switch (tier) {
            case BASIC -> 1;
            case ADVANCED -> 2;
            case ELITE -> 4;
        };
        return (int) Math.round(base * Config.TACZ_AMMO_MULTIPLIER.get() * tierFactor);
    }

    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T value = list.get(i);
            list.set(i, list.get(j));
            list.set(j, value);
        }
    }

    private TaczLootFiller() {
    }
}
