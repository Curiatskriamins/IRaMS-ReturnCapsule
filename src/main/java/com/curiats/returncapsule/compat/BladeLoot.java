package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BladeLoot {
    private static volatile List<Blade> cache;

    public static void fill(Container container, SummonTier tier, RandomSource random, Level level) {
        if (tier != SummonTier.ELITE && random.nextDouble() > Config.UNIQUE_WEAPON_CHANCE.get()) {
            return;
        }
        List<Blade> pool = pool(tier, level);
        if (pool.isEmpty()) {
            return;
        }
        Blade blade = pool.get(random.nextInt(pool.size()));
        new ItemInserter(container).insert(blade.stack().copy());
    }

    private static List<Blade> pool(SummonTier tier, Level level) {
        List<Blade> result = new ArrayList<>();
        for (Blade blade : all(level)) {
            boolean match = switch (tier) {
                case BASIC -> blade.damage() < 7.0D;
                case ADVANCED -> blade.damage() >= 7.0D && blade.damage() <= 19.0D;
                case ELITE -> blade.damage() > 19.0D;
            };
            if (match) {
                result.add(blade);
            }
        }
        if (result.isEmpty()) {
            List<Blade> all = all(level);
            double max = Double.NEGATIVE_INFINITY;
            for (Blade blade : all) {
                max = Math.max(max, blade.damage());
            }
            for (Blade blade : all) {
                if (blade.damage() >= max - 1.0E-4D) {
                    result.add(blade);
                }
            }
        }
        return result;
    }

    private static List<Blade> all(Level level) {
        List<Blade> local = cache;
        if (local == null) {
            synchronized (BladeLoot.class) {
                local = cache;
                if (local == null) {
                    local = build(level);
                    cache = local;
                }
            }
        }
        return local;
    }

    private static List<Blade> build(Level level) {
        List<Blade> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!isAllowed(item)) {
                continue;
            }
            ItemStack stack = item.getDefaultInstance();
            result.add(new Blade(stack, damage(stack)));
        }
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            for (ItemStack stack : tab.getDisplayItems()) {
                if (!isAllowed(stack.getItem())) {
                    continue;
                }
                result.add(new Blade(stack.copy(), damage(stack)));
            }
        }
        addRegistryBlades(result, level);
        return List.copyOf(result);
    }

    private static void addRegistryBlades(List<Blade> result, Level level) {
        if (level == null) {
            return;
        }
        try {
            Class<?> definition = Class.forName("mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition");
            Field keyField = definition.getField("REGISTRY_KEY");
            Object key = keyField.get(null);
            if (!(key instanceof ResourceKey<?> resourceKey)) {
                return;
            }
            Registry<?> registry = level.registryAccess().registryOrThrow((ResourceKey<? extends Registry<?>>) resourceKey);
            Method getBlade = definition.getMethod("getBlade");
            for (Object entry : registry) {
                Object value = getBlade.invoke(entry);
                if (value instanceof ItemStack stack && isAllowed(stack.getItem())) {
                    result.add(new Blade(stack.copy(), damage(stack)));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static boolean isAllowed(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && isBladeNamespace(id.getNamespace()) && isSlashBlade(item);
    }

    private static boolean isSlashBlade(Item item) {
        String name = item.getClass().getName();
        return name.equals("mods.flammpfeil.slashblade.item.ItemSlashBlade")
                || name.endsWith(".ItemSlashBlade");
    }

    private static boolean isBladeNamespace(String namespace) {
        String lower = namespace.toLowerCase(Locale.ROOT);
        for (String allowed : Config.BLADE_NAMESPACES.get()) {
            if (allowed != null && lower.equals(allowed.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static double damage(ItemStack stack) {
        double bonus = 0.0D;
        for (Map.Entry<Attribute, AttributeModifier> entry
                : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).entries()) {
            if (entry.getKey() == Attributes.ATTACK_DAMAGE) {
                bonus += entry.getValue().getAmount();
            }
        }
        return 1.0D + bonus;
    }

    private record Blade(ItemStack stack, double damage) {
    }

    private BladeLoot() {
    }
}
