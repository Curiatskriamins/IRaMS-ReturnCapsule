package com.curiats.returncapsule.loot;

import com.curiats.returncapsule.ReturnCapsuleMod;
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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds complete armour sets at runtime from the registry.
 *
 * <p>No name matching: every {@link ArmorItem} is grouped by its {@link ArmorMaterial}, so a
 * set is "all four slots sharing one material". Sets are ranked by total defence + toughness,
 * which automatically follows the pack: vanilla leather sits at the bottom, whatever the pack
 * considers top tier (SRP, netherite, modded power armour) sits at the top.</p>
 */
public final class ArmorSets {
    private static final List<ArmorItem.Type> SLOTS = List.of(
            ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS);

    private static volatile List<Set> cache;

    public static void fill(Container container, SummonTier tier, RandomSource random) {
        List<Set> all = sets();
        if (all.isEmpty()) {
            return;
        }
        int size = all.size();
        int from = switch (tier) {
            case BASIC -> 0;
            case ADVANCED -> size / 4;
            case ELITE -> size / 2;
        };
        int to = switch (tier) {
            case BASIC -> Math.max(1, size * 2 / 5);
            case ADVANCED -> Math.max(from + 1, size * 3 / 4);
            case ELITE -> size;
        };
        Set set = all.get(from + random.nextInt(Math.max(1, to - from)));
        ItemInserter inserter = new ItemInserter(container);
        inserter.insert(set.helmet());
        inserter.insert(set.chest());
        inserter.insert(set.legs());
        inserter.insert(set.boots());
    }

    private record Set(ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots, int power) {
    }

    private static List<Set> sets() {
        List<Set> local = cache;
        if (local == null) {
            synchronized (ArmorSets.class) {
                local = cache;
                if (local == null) {
                    local = build();
                    cache = local;
                }
            }
        }
        return local;
    }

    private static List<Set> build() {
        // Group by the ArmorMaterial object, not by its name: two mods may both call their
        // material "steel", and mixing their pieces would produce a broken set.
        Map<ArmorMaterial, EnumMap<ArmorItem.Type, Item>> byMaterial = new IdentityHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof ArmorItem armor)) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || ReturnCapsuleMod.MODID.equals(id.getNamespace())) {
                continue;
            }
            byMaterial.computeIfAbsent(armor.getMaterial(), ignored -> new EnumMap<>(ArmorItem.Type.class))
                    .putIfAbsent(armor.getType(), item);
        }

        List<Set> result = new ArrayList<>();
        for (Map.Entry<ArmorMaterial, EnumMap<ArmorItem.Type, Item>> entry : byMaterial.entrySet()) {
            EnumMap<ArmorItem.Type, Item> slots = entry.getValue();
            if (!slots.keySet().containsAll(SLOTS)) {
                continue;
            }
            ArmorMaterial material = entry.getKey();
            int power = 0;
            for (ArmorItem.Type type : SLOTS) {
                power += itemPower(slots.get(type), type, material);
            }
            result.add(new Set(
                    new ItemStack(slots.get(ArmorItem.Type.HELMET)),
                    new ItemStack(slots.get(ArmorItem.Type.CHESTPLATE)),
                    new ItemStack(slots.get(ArmorItem.Type.LEGGINGS)),
                    new ItemStack(slots.get(ArmorItem.Type.BOOTS)),
                    power));
        }
        result.sort(Comparator.comparingInt(Set::power));
        ReturnCapsuleMod.LOGGER.info("Indexed {} complete armour sets for capsule loot", result.size());
        return List.copyOf(result);
    }

    /**
     * Score from the item's real attribute modifiers first (that is what the game uses for
     * armour points and covers modded bonuses), and fall back to the material's base values.
     */
    private static int itemPower(Item item, ArmorItem.Type type, ArmorMaterial material) {
        EquipmentSlot slot = switch (type) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
        };
        int score = 0;
        for (Map.Entry<Attribute, AttributeModifier> entry : item.getDefaultAttributeModifiers(slot).entries()) {
            Attribute attribute = entry.getKey();
            if (attribute == Attributes.ARMOR || attribute == Attributes.ARMOR_TOUGHNESS) {
                score += (int) Math.round(entry.getValue().getAmount() * 10.0D);
            }
        }
        if (score == 0) {
            score = material.getDefenseForType(type) * 10 + (int) (material.getToughness() * 10.0F);
        }
        return score;
    }

    private ArmorSets() {
    }
}
