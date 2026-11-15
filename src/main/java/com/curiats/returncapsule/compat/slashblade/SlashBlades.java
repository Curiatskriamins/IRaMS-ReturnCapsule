package com.curiats.returncapsule.compat.slashblade;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * SlashBlade Resharped bridge. The mod is not a compile time dependency, so everything is looked up reflectively:
 * <ul>
 *   <li>telling a blade apart from other {@code slashblade:*} items (a proud soul is not a blade), which only the real
 *       item class can answer — there is no item tag or marker interface in that mod;</li>
 *   <li>building a valid starter blade, which has to come from the data driven blade definition registry, because a bare
 *       {@code new ItemStack(item)} carries no blade state and renders and hits like an empty item;</li>
 *   <li>driving the combo state machine for a mob swing, which the mod leaves to the player's input driver otherwise.</li>
 * </ul>
 */
public final class SlashBlades {
    private static final String ITEM_CLASS = "mods.flammpfeil.slashblade.item.ItemSlashBlade";
    private static final String STATE_CLASS = "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState";
    private static final String BUILTIN_CLASS = "mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry";
    private static final String MAIN_CLASS = "mods.flammpfeil.slashblade.SlashBlade";
    private static final String LAZY_OPTIONAL = "net.minecraftforge.common.util.LazyOptional";
    private static final String PREFERRED_BLADE = "SABIGATANA";
    private static final String FALLBACK_BLADE = "YAMATO";

    private static volatile boolean resolved;
    private static volatile Class<?> bladeItemClass;
    private static volatile Class<?> bladeStateClass;
    private static volatile Field bladeCapabilityField;
    private static volatile Method getCapability;
    private static volatile Method lazyOrElse;
    private static volatile Method setTargetEntityId;
    private static volatile Method progressCombo;
    private static volatile Method definitionRegistry;
    private static volatile Method registryGetOrThrow;
    private static volatile Method holderValue;
    private static volatile Object preferredKey;
    private static volatile Object fallbackKey;

    public static boolean isBlade(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Class<?> type = bladeItemClass();
        return type != null && type.isInstance(stack.getItem());
    }

    /** A ready to use blade from the definition registry, or {@link ItemStack#EMPTY} when unavailable. */
    public static ItemStack defaultBlade(Level level) {
        if (!resolve()) {
            return ItemStack.EMPTY;
        }
        ItemStack blade = bladeFrom(level, preferredKey);
        return blade.isEmpty() ? bladeFrom(level, fallbackKey) : blade;
    }

    /**
     * Advances the slashblade combo state for a mob swing.
     * <p>
     * The combo engine is already {@code LivingEntity}-generic; what a mob lacks is the player input driver that feeds
     * it. This performs the same two steps that driver does — point the blade at the victim, then progress the combo —
     * which runs the combo's own motion and animation while leaving damage to the caller's existing hit resolution.
     * <p>
     * Reference: the maid addon {@code native-power-of-maid} drives exactly these {@code ISlashBladeState} entry points
     * from a mob AI ({@code setTargetEntityId}, {@code progressCombo}, {@code resolvCurrentComboStateTicks},
     * {@code getSlashArts().doArts(...)}).
     */
    public static void advanceCombo(LivingEntity user, @Nullable Entity target) {
        if (!resolve() || getCapability == null || progressCombo == null) {
            return;
        }
        try {
            Object capability = bladeCapabilityField == null ? null : bladeCapabilityField.get(null);
            if (capability == null) {
                return;
            }
            Object optional = getCapability.invoke(user.getMainHandItem(), capability);
            Object state = optional == null ? null : lazyOrElse.invoke(optional, (Object) null);
            if (state == null) {
                return;
            }
            if (target != null && setTargetEntityId != null) {
                setTargetEntityId.invoke(state, target);
            }
            progressCombo.invoke(state, user);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("Could not advance the slashblade combo: {}", exception.toString());
        }
    }

    private static ItemStack bladeFrom(Level level, Object key) {
        if (key == null || definitionRegistry == null || registryGetOrThrow == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object registry = definitionRegistry.invoke(null, level);
            if (registry == null) {
                return ItemStack.EMPTY;
            }
            Object holder = registryGetOrThrow.invoke(registry, key);
            if (!(holder instanceof Holder<?> reference)) {
                return ItemStack.EMPTY;
            }
            Object definition = reference.value();
            Method getBlade = definition.getClass().getMethod("getBlade");
            Object stack = getBlade.invoke(definition);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("Could not read the default slashblade: {}", exception.toString());
            return ItemStack.EMPTY;
        }
    }

    private static Class<?> bladeItemClass() {
        resolve();
        return bladeItemClass;
    }

    private static boolean resolve() {
        if (resolved) {
            return bladeItemClass != null;
        }
        synchronized (SlashBlades.class) {
            if (resolved) {
                return bladeItemClass != null;
            }
            resolved = true;
            try {
                bladeItemClass = Class.forName(ITEM_CLASS);
                Class<?> builtin = Class.forName(BUILTIN_CLASS);
                Class<?> main = Class.forName(MAIN_CLASS);
                definitionRegistry = main.getMethod("getSlashBladeDefinitionRegistry", Level.class);
                registryGetOrThrow = Registry.class.getMethod("getOrThrow", ResourceKey.class);
                holderValue = Holder.class.getMethod("value");
                preferredKey = builtinKey(builtin, PREFERRED_BLADE);
                fallbackKey = builtinKey(builtin, FALLBACK_BLADE);
                resolveComboApi();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                bladeItemClass = null;
                ReturnCapsuleMod.LOGGER.debug("SlashBlade is not available: {}", exception.toString());
            }
            return bladeItemClass != null;
        }
    }

    /** The combo hooks are optional: a fork without them should still give us blade detection and starter blades. */
    private static void resolveComboApi() {
        try {
            bladeStateClass = Class.forName(STATE_CLASS);
            bladeCapabilityField = bladeItemClass.getField("BLADESTATE");
            getCapability = ItemStack.class.getMethod("getCapability", Capability.class);
            lazyOrElse = Class.forName(LAZY_OPTIONAL).getMethod("orElse", Object.class);
            setTargetEntityId = bladeStateClass.getMethod("setTargetEntityId", Entity.class);
            progressCombo = bladeStateClass.getMethod("progressCombo", LivingEntity.class);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            bladeStateClass = null;
            bladeCapabilityField = null;
            getCapability = null;
            lazyOrElse = null;
            setTargetEntityId = null;
            progressCombo = null;
            ReturnCapsuleMod.LOGGER.debug("SlashBlade combo hooks unavailable: {}", exception.toString());
        }
    }

    private static Object builtinKey(Class<?> builtin, String name) {
        try {
            Field field = builtin.getField(name);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private SlashBlades() {
    }
}
