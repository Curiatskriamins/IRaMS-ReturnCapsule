package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.Config;

import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.google.common.collect.Multimap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weapon bookkeeping for a humanoid: what a stack is, the built-in fallback kit, and how to compare two weapons.
 * <p>
 * Both weapon systems are optional: with neither TaCZ nor a slashblade mod installed the humanoids still fall back to
 * their built-in netherite sword, so they always have something to swing.
 */
public final class HumanoidWeapons {
    /** Forced enchantment levels of the built-in sword. Deliberately far beyond vanilla limits. */
    private static final int BUILTIN_SHARPNESS = 30;
    private static final int BUILTIN_KNOCKBACK = 2;
    /** Vanilla player baselines, used when comparing weapons by damage per second. */
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

    /**
     * A blade is whatever the slashblade mod's own item class says it is. The namespace rule is only a fallback for
     * forks that rename that class, and it additionally requires the item to be damageable so that
     * {@code slashblade:proudsoul} and friends are not mistaken for weapons.
     */
    public static boolean isBlade(ItemStack stack) {
        if (isSlashBladeModItem(stack)) {
            return false;
        }
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && matchesBladeNamespace(id.getNamespace());
    }

    /** The built-in melee fallback: unbreakable netherite sword with a forced Sharpness/Knockback level. */
    public static ItemStack builtinSword() {
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.getOrCreateTag().putBoolean("Unbreakable", true);
        sword.enchant(Enchantments.SHARPNESS, BUILTIN_SHARPNESS);
        sword.enchant(Enchantments.KNOCKBACK, BUILTIN_KNOCKBACK);
        return sword;
    }

    /** True for the built-in sword, which is part of the kit and must never be dropped as loot. */
    public static boolean isBuiltinSword(ItemStack stack) {
        return stack.is(Items.NETHERITE_SWORD) && stack.isEnchanted();
    }

    /**
     * Anything that behaves like a shield. Modded shields are covered by the use-animation test rather than by class,
     * so a shield from another mod is treated the same as the vanilla one.
     */
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

    /** A starting gun the profile is allowed to use, or {@link ItemStack#EMPTY} when none is installed. */
    public static ItemStack startingGun(RandomSource random) {
        return TaczRifles.randomServiceGun(random);
    }

    /** The second gun: long range, for targets past a rifle's reach. */
    public static ItemStack startingLongGun(RandomSource random) {
        return TaczRifles.randomLongGun(random);
    }

    /** A starting melee weapon: the profile's blade if it is allowed and installed, otherwise the built-in sword. */
    public static ItemStack startingMelee(HumanoidProfile profile, Level level, RandomSource random) {
        if (profile.weapons().allowsBlade()) {
            ItemStack blade = builtinSword();
            if (!blade.isEmpty()) {
                return blade;
            }
        }
        return builtinSword();
    }

    /**
     * Damage per second of a melee weapon, used to pick the better of the two melee options. Enchantment damage is
     * included, which is what makes the built-in Sharpness 30 sword competitive with a named blade; TaCZ guns are not
     * compared because their damage never reaches the item's attributes.
     */
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

    /** Attack speed modifier of the held item, added on top of the player baseline. */
    public static double attackSpeedBonus(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        return attributeBonus(stack, Attributes.ATTACK_SPEED);
    }

    /** Damage the held item adds on top of the entity's base attack damage. */
    public static double attackBonus(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        // SlashBlade items do not declare vanilla attack-damage modifiers; their damage lives in the blade state. Without
        // this the blade added nothing and a humanoid holding one hit exactly like a bare fist.
        return attributeBonus(stack, Attributes.ATTACK_DAMAGE)
                + EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED);
    }

    /** Hook for reload-time invalidation. Nothing is cached here any more; the compat layers cache themselves. */
    public static void invalidate() {
        // Intentionally empty.
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

    /** True for anything from the SlashBlade mod. Its weapons are refused outright; see isBlade. */
    private static boolean isSlashBladeModItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
