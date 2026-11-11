package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.util.ItemInserter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

 
public final class BladeLoot {
    private static volatile List<Blade> cache;

    public static void fill(Container container, SummonTier tier, RandomSource random) {
        if (random.nextDouble() > Config.UNIQUE_WEAPON_CHANCE.get()) {
            return;
        }
        List<Blade> pool = pool(tier);
        if (pool.isEmpty()) {
            return;
        }
        Blade blade = pool.get(random.nextInt(pool.size()));
        new ItemInserter(container).insert(new ItemStack(blade.item()));
    }

    private static List<Blade> pool(SummonTier tier) {
        List<Blade> result = new ArrayList<>();
        for (Blade blade : all()) {
            boolean match = switch (tier) {
                case BASIC -> blade.damage() < 7.0D;
                case ADVANCED -> blade.damage() >= 7.0D && blade.damage() <= 19.0D;
                case ELITE -> blade.damage() > 19.0D;
            };
            if (match) {
                result.add(blade);
            }
        }
        return result;
    }

    private static List<Blade> all() {
        List<Blade> local = cache;
        if (local == null) {
            synchronized (BladeLoot.class) {
                local = cache;
                if (local == null) {
                    local = build();
                    cache = local;
                }
            }
        }
        return local;
    }

    private static List<Blade> build() {
        List<Blade> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !isBladeNamespace(id.getNamespace())) {
                continue;
            }
            if (item.getMaxStackSize() != 1 || item.getMaxDamage() <= 0) {
                continue;
            }
            result.add(new Blade(item, damage(item)));
        }
        return List.copyOf(result);
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

     
    private static double damage(Item item) {
        double bonus = 0.0D;
        for (Map.Entry<Attribute, AttributeModifier> entry
                : item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND).entries()) {
            if (entry.getKey() == Attributes.ATTACK_DAMAGE) {
                bonus += entry.getValue().getAmount();
            }
        }
        return 1.0D + bonus;
    }

    private record Blade(Item item, double damage) {
    }

    private BladeLoot() {
    }
}
