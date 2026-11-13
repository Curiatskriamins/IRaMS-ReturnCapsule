package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AmmoSupplyCompat {
    private static final String MOD_ID = "ammo_supply_box";
    private static volatile List<Item> boxes;

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static ItemStack box(SummonTier tier) {
        List<Item> list = boxes();
        if (list.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int index = switch (tier) {
            case BASIC -> 0;
            case ADVANCED -> list.size() / 2;
            case ELITE -> list.size() - 1;
        };
        return new ItemStack(list.get(index));
    }

    private static List<Item> boxes() {
        List<Item> local = boxes;
        if (local == null) {
            synchronized (AmmoSupplyCompat.class) {
                local = boxes;
                if (local == null) {
                    local = build();
                    boxes = local;
                }
            }
        }
        return local;
    }

    private static List<Item> build() {
        List<Item> result = new ArrayList<>();
        try {
            Class<?> typesClass = Class.forName("com.koei.ammo_supply_box.block.AmmoBoxTypes");
            Object all = typesClass.getField("ALL").get(null);
            Class<?> typeClass = Class.forName("com.koei.ammo_supply_box.block.AmmoBoxTypes$Type");
            Method id = typeClass.getMethod("id");
            Method variant = typeClass.getMethod("variant");
            Method budget = typeClass.getMethod("defaultDamageBudget");
            Object normal = Class.forName("com.koei.ammo_supply_box.block.AmmoBoxTypes$Variant")
                    .getField("NORMAL").get(null);
            Method ammoBox = Class.forName("com.koei.ammo_supply_box.registry.ModItems")
                    .getMethod("ammoBox", String.class);
            List<Object> types = new ArrayList<>();
            for (Object type : (Iterable<?>) all) {
                if (variant.invoke(type) == normal) {
                    types.add(type);
                }
            }
            types.sort(Comparator.comparingInt(type -> {
                try {
                    return (int) budget.invoke(type);
                } catch (ReflectiveOperationException exception) {
                    return 0;
                }
            }));
            for (Object type : types) {
                Object object = ammoBox.invoke(null, id.invoke(type));
                if (object instanceof net.minecraftforge.registries.RegistryObject<?> registry
                        && registry.get() instanceof Item item) {
                    result.add(item);
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }
        return List.copyOf(result);
    }

    private AmmoSupplyCompat() {
    }
}
