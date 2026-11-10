package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lazily built pool of items from every installed mod except vanilla, Forge and this mod.
 *
 * <p>Only "safe" items are kept: blocks and spawn eggs are skipped, as are unique items that
 * neither stack nor have durability (those are usually NBT driven, for example guns, backpacks
 * or wands). The default config also excludes the gun mods whose items need NBT to work.</p>
 */
public final class ModItemPool {
    private static final Set<String> INTERNAL_NAMESPACES =
            Set.of("minecraft", "forge", "returncapsule", "fml", "neoforge");
    private static final int MAX_PER_STACK = 3;

    private static final Map<SummonTier, List<Entry>> TEMPLATES = new EnumMap<>(SummonTier.class);

    public static ItemStack random(RandomSource random, SummonTier tier) {
        List<Entry> pool = templates(tier);
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int total = 0;
        for (Entry entry : pool) {
            total += entry.weight();
        }
        int roll = random.nextInt(total);
        for (Entry entry : pool) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry.stack().copy();
            }
        }
        return pool.get(pool.size() - 1).stack().copy();
    }

    public static ItemStack random(RandomSource random) {
        return random(random, SummonTier.BASIC);
    }

    public static void fill(Container container, SummonTier tier, RandomSource random, int rolls) {
        if (!Config.ADD_MOD_ITEMS.get() || rolls <= 0) {
            return;
        }
        ItemInserter inserter = new ItemInserter(container);
        for (int i = 0; i < rolls; i++) {
            ItemStack stack = random(random, tier);
            if (stack.isEmpty()) {
                continue;
            }
            int max = Math.min(MAX_PER_STACK, stack.getMaxStackSize());
            int min = Math.min(1, max);
            stack.setCount(max <= min ? min : min + random.nextInt(max - min + 1));
            if (!inserter.insert(stack)) {
                return;
            }
        }
        container.setChanged();
    }

    private static List<Entry> templates(SummonTier tier) {
        List<Entry> local = TEMPLATES.get(tier);
        if (local == null) {
            synchronized (ModItemPool.class) {
                local = TEMPLATES.get(tier);
                if (local == null) {
                    local = build(tier);
                    TEMPLATES.put(tier, local);
                }
            }
        }
        return local;
    }

    private static List<Entry> build(SummonTier tier) {
        List<Entry> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null || !isSafeModItem(id, item)) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    continue;
                }
                int weight = LootRarity.weight(tier, stack);
                if (weight > 0) {
                    result.add(new Entry(stack, weight));
                }
            } catch (RuntimeException exception) {
                ReturnCapsuleMod.LOGGER.debug("Skipping mod item {}: {}", item, exception.toString());
            }
        }
        if (result.isEmpty() && tier != SummonTier.BASIC) {
            SummonTier fallback = SummonTier.values()[tier.ordinal() - 1];
            return build(fallback);
        }
        ReturnCapsuleMod.LOGGER.info("Collected {} safe mod items for tier {}", result.size(), tier.id());
        return List.copyOf(result);
    }

    private static boolean isSafeModItem(ResourceLocation id, Item item) {
        if (INTERNAL_NAMESPACES.contains(id.getNamespace()) || isExcludedNamespace(id.getNamespace())) {
            return false;
        }
        if (item instanceof BlockItem || item instanceof SpawnEggItem) {
            return false;
        }
        String path = id.getPath();
        if (path.contains("spawn_egg") || path.contains("debug") || path.contains("dummy")
                || path.contains("placeholder") || path.contains("creative")) {
            return false;
        }
        ItemStack stack = new ItemStack(item);
        return stack.getMaxStackSize() > 1 || stack.getMaxDamage() > 0;
    }

    private static boolean isExcludedNamespace(String namespace) {
        String lower = namespace.toLowerCase(Locale.ROOT);
        for (String excluded : Config.MOD_LOOT_EXCLUDED_NAMESPACES.get()) {
            if (excluded == null) {
                continue;
            }
            String needle = excluded.trim().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private record Entry(ItemStack stack, int weight) {
    }

    private ModItemPool() {
    }
}
