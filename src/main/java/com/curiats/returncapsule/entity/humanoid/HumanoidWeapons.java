package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.Config;

import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Map;
import net.minecraftforge.registries.ForgeRegistries;

public final class HumanoidWeapons {

    private static final int BUILTIN_SHARPNESS = 30;
    private static final int BUILTIN_KNOCKBACK = 2;

    private static final double BASE_ATTACK_DAMAGE = 1.0D;
    private static final double BASE_ATTACK_SPEED = 4.0D;

    public static WeaponKind kindOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return WeaponKind.NONE;
        }
        if (TaczRifles.isServiceGun(stack)) {
            return WeaponKind.GUN;
        }
        if (isBlade(stack)) {
            return WeaponKind.BLADE;
        }
        return WeaponKind.NONE;
    }

    public static boolean isBlade(ItemStack stack) {
        if (isSlashBladeModItem(stack)) {
            return false;
        }
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && matchesBladeNamespace(id.getNamespace());
    }

    public static ItemStack builtinSword() {
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.getOrCreateTag().putBoolean("Unbreakable", true);
        sword.enchant(Enchantments.SHARPNESS, BUILTIN_SHARPNESS);
        sword.enchant(Enchantments.KNOCKBACK, BUILTIN_KNOCKBACK);
        return sword;
    }

    public static boolean isBuiltinSword(ItemStack stack) {
        return stack.is(Items.NETHERITE_SWORD) && stack.isEnchanted();
    }

    public static boolean isShield(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof ShieldItem) {
            return true;
        }
        return stack.getUseAnimation() == UseAnim.BLOCK && stack.getUseDuration() > 0;
    }

    public static ItemStack builtinShield() {
        return new ItemStack(Items.SHIELD);
    }

    public static ItemStack startingGun(RandomSource random) {
        return TaczRifles.randomServiceGun(random);
    }

    public static ItemStack startingLongGun(RandomSource random) {
        return TaczRifles.randomLongGun(random);
    }

    public static ItemStack startingMelee(HumanoidProfile profile, Level level, RandomSource random) {
        if (profile.weapons().allowsBlade()) {
            ItemStack blade = builtinSword();
            if (!blade.isEmpty()) {
                return blade;
            }
        }
        return builtinSword();
    }

    public static double dps(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        double damage = BASE_ATTACK_DAMAGE
                + attributeBonus(stack, Attributes.ATTACK_DAMAGE)
                + EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED);
        double speed = BASE_ATTACK_SPEED + attributeBonus(stack, Attributes.ATTACK_SPEED);
        return damage * Math.max(0.1D, speed);
    }

    public static double attackSpeedBonus(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        return attributeBonus(stack, Attributes.ATTACK_SPEED);
    }

    public static double attackBonus(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }

        return attributeBonus(stack, Attributes.ATTACK_DAMAGE)
                + EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED);
    }

    public static void invalidate() {

    }

    private static double attributeBonus(ItemStack stack, Attribute attribute) {
        double bonus = 0.0D;
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            if (entry.getKey() != attribute) {
                continue;
            }
            AttributeModifier modifier = entry.getValue();
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                bonus += modifier.getAmount();
            }
        }
        return bonus;
    }

    private static boolean isSlashBladeModItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().startsWith("slashblade");
    }

    private static boolean matchesBladeNamespace(String namespace) {
        String lower = namespace.toLowerCase(Locale.ROOT);
        for (String candidate : Config.BLADE_NAMESPACES.get()) {
            if (candidate == null) {
                continue;
            }
            String needle = candidate.trim().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private HumanoidWeapons() {
    }
}
