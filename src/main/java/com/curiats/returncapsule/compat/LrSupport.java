package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LrSupport {
    private static final String MOD_ID = "lrtactical";
    private static final String THROWABLE_MARKER = "ThrowableItem";
    private static final String CONSUMABLE_MARKER = "ConsumableItem";
    private static final String MELEE_MARKER = "MeleeItem";

    private static final List<String> THROWABLE_PRIORITY = List.of(
            "m67", "rgn", "smoke_grenade", "flash_grenade", "molotov", "c4");
    private static final List<String> CONSUMABLE_PRIORITY = List.of(
            "ai2", "amoxycillin", "blood_pack", "carfak", "cms", "condensed_milk",
            "goldenstar", "ibuprofen", "surv12", "vaseline");
    private static final List<String> MELEE_PRIORITY = List.of("dagger", "baseball_bat", "karambit");

    private static final Set<String> SKIPPED_PATHS = Set.of("consumable", "throwable", "melee");

    private static final Map<String, List<Item>> POOLS = new HashMap<>();

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void add(ItemInserter inserter, RandomSource random) {
        addCategory(inserter, random, THROWABLE_MARKER, THROWABLE_PRIORITY, 4, 8);
        addCategory(inserter, random, CONSUMABLE_MARKER, CONSUMABLE_PRIORITY, 5, 10);
        addCategory(inserter, random, MELEE_MARKER, MELEE_PRIORITY, 1, 2);
    }

    private static void addCategory(ItemInserter inserter, RandomSource random, String marker,
                                    List<String> priority, int min, int max) {
        int amount = min + random.nextInt(max - min + 1);
        Set<Item> added = new HashSet<>();
        for (String path : priority) {
            if (added.size() >= amount) {
                break;
            }
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(MOD_ID, path));
            if (item != Items.AIR && added.add(item)) {
                inserter.insert(new ItemStack(item));
            }
        }
        List<Item> pool = pool(marker);
        int offset = pool.isEmpty() ? 0 : random.nextInt(pool.size());
        for (int i = 0; i < pool.size() && added.size() < amount; i++) {
            Item item = pool.get((offset + i) % pool.size());
            if (added.add(item)) {
                inserter.insert(new ItemStack(item));
            }
        }
    }

    private static List<Item> pool(String marker) {
        List<Item> cached = POOLS.get(marker);
        if (cached != null) {
            return cached;
        }
        List<Item> built = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!MOD_ID.equals(id.getNamespace()) || SKIPPED_PATHS.contains(id.getPath())) {
                continue;
            }
            Item candidate = BuiltInRegistries.ITEM.get(id);
            if (candidate != Items.AIR && candidate.getClass().getName().contains(marker)) {
                built.add(candidate);
            }
        }
        List<Item> frozen = List.copyOf(built);
        POOLS.put(marker, frozen);
        ReturnCapsuleMod.LOGGER.debug("Cached {} lrtactical items for {}", frozen.size(), marker);
        return frozen;
    }

    public static void invalidate() {
        POOLS.clear();
    }

    private LrSupport() {
    }
}
