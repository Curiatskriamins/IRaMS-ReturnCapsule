package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Armour material of the base humanoid set.
 *
 * <p>The material is deliberately heavier than netherite on toughness: against the parasite mod's large single hits the
 * vanilla absorb formula is dominated by the toughness term, and twenty toughness is where it reaches its own ceiling.</p>
 */
public enum ModArmorMaterials implements ArmorMaterial {
    BASE_HUMANOID("base_humanoid", 40, 10.0F, 0.15F, 15, SoundEvents.ARMOR_EQUIP_NETHERITE);

    private final String name;
    private final int durabilityMultiplier;
    private final float constructorToughness;
    private final float knockbackResistance;
    private final int enchantmentValue;
    private final SoundEvent equipSound;

    ModArmorMaterials(String name, int durabilityMultiplier, float toughness, float knockbackResistance,
                      int enchantmentValue, SoundEvent equipSound) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.constructorToughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        int base = switch (type) {
            case HELMET -> 11;
            case CHESTPLATE -> 16;
            case LEGGINGS -> 15;
            case BOOTS -> 13;
        };
        return base * this.durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 3;
            case CHESTPLATE -> 8;
            case LEGGINGS -> 6;
            case BOOTS -> 3;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return this.equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.IRON_INGOT);
    }

    @Override
    public String getName() {
        return ReturnCapsuleMod.MODID + ":" + this.name;
    }

    @Override
    public float getToughness() {
        // Per piece, so a full set is four times this. Toughness is what keeps the armour term of the absorb formula
        // (armour - damage / (2 + toughness / 4)) from collapsing against large single hits, which is exactly the
        // parasite mod's damage profile; armour points alone flatten out at the twenty point ceiling.
        try {
            return com.curiats.returncapsule.Config.EQUIPMENT_ARMOR_TOUGHNESS.get().floatValue();
        } catch (RuntimeException notLoaded) {
            return this.constructorToughness;
        }
    }

    @Override
    public float getKnockbackResistance() {
        return this.knockbackResistance;
    }
}
