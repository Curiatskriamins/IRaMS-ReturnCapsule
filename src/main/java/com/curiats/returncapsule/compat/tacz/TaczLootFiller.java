package com.curiats.returncapsule.compat.tacz;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.compat.LrSupport;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TaczLootFiller {
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
        ItemInserter inserter = new ItemInserter(container);

        for (int i = 0; i < gunCount; i++) {
            GunInfo gun = pool.get(i);
            inserter.insertNoMerge(buildGun(gun));
            insertAttachments(inserter, attachments, random);
            if (com.curiats.returncapsule.compat.AmmoSupplyCompat.isLoaded()) {
                ItemStack box = com.curiats.returncapsule.compat.AmmoSupplyCompat.box(tier);
                if (!box.isEmpty()) {
                    inserter.insertNoMerge(box);
                }
            } else {
                addAmmo(inserter, gun.ammoId(), ammoCount(gun.type(), random, tier));
            }
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
                inserter.insertNoMerge(buildAttachment(id));
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
            inserter.insertNoMerge(buildAttachment(attachments.get(index)));
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
        return TaczItems.gun(gun, 0);
    }

    private static ItemStack buildAttachment(ResourceLocation attachmentId) {
        return TaczItems.attachment(attachmentId);
    }

    private static void addAmmo(ItemInserter inserter, ResourceLocation ammoId, int count) {
        if (ammoId == null || count <= 0) {
            return;
        }
        int remaining = count;
        while (remaining > 0) {
            ItemStack stack = TaczItems.ammo(ammoId, remaining);
            if (stack.isEmpty()) {
                return;
            }
            int moved = stack.getCount();
            if (!inserter.insertNoMerge(stack)) {
                return;
            }
            remaining -= moved;
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
