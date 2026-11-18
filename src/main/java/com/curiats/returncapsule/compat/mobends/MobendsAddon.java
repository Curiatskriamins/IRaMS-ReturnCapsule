package com.curiats.returncapsule.compat.mobends;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.BaseHumanoid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class MobendsAddon {
    private static final String MOD_ID = "mobends";
    private static final String ADDON_ID = "returncapsule_humanoids";
    private static final String DISPLAY_NAME = "Return Capsule humanoid";

    private static final String ADDONS = "goblinbob.mobends.api.addon.Addons";
    private static final String I_ADDON = "goblinbob.mobends.api.addon.IAddon";
    private static final String I_DATA_FACTORY = "goblinbob.mobends.core.data.IEntityDataFactory";
    private static final String I_MUTATOR_FACTORY = "goblinbob.mobends.core.mutators.IMutatorFactory";
    private static final String MUTATED_RENDERER = "goblinbob.mobends.core.client.MutatedRenderer";
    private static final String I_PREVIEWER = "goblinbob.mobends.core.bender.IPreviewer";
    private static final String HUMANOID_DATA = "goblinbob.mobends.standard.data.HumanoidMobData";

    private static final String HUMANOID_MUTATOR = "goblinbob.mobends.standard.mutators.HumanoidMobMutator";
    private static final String BIPED_RENDERER = "goblinbob.mobends.standard.client.renderer.entity.mutated.BipedRenderer";
    private static final String BIPED_PREVIEWER = "goblinbob.mobends.standard.previewer.BipedPreviewer";
    private static final String DEFAULT_ADDON = "goblinbob.mobends.standard.DefaultAddon";
    private static final String BENDER_REGISTRY = "goblinbob.mobends.core.bender.EntityBenderRegistry";
    private static final String BENDER = "goblinbob.mobends.core.bender.EntityBender";
    private static final String CORE_CLIENT_CONFIG = "goblinbob.mobends.core.configuration.CoreClientConfig";
    private static final String MUTATOR = "goblinbob.mobends.core.mutators.Mutator";
    private static final String LIVING_ENTITY_DATA = "goblinbob.mobends.core.data.LivingEntityData";
    private static final String ANIMATION_CONTROL = "goblinbob.mobends.api.animation.MoBendsAnimationControl";

    private static final String[] FALLBACK_ANIMATIONS =
            {"walk", "sprint", "jump", "fall", "sneak", "swim", "attack", "ride", "climb"};
    private static final String[] FALLBACK_PARTS =
            {"head", "body", "leftArm", "rightArm", "leftForeArm", "rightForeArm",
                    "leftLeg", "rightLeg", "leftForeLeg", "rightForeLeg"};

    private static boolean attempted;

    private static volatile Method getForEntity;
    private static volatile Method getMutator;
    private static volatile Method getOrMakeData;
    private static volatile Method onAttack;
    private static volatile Method isAnimated;
    private static volatile Object registryInstance;

    private static volatile String registeredKey;

    public static void setAnimationEnabled(boolean enabled) {
        if (!ModList.get().isLoaded(MOD_ID) || registeredKey == null) {
            return;
        }
        try {
            ClassLoader loader = MobendsAddon.class.getClassLoader();
            Class<?> registryClass = Class.forName(BENDER_REGISTRY, false, loader);
            Object registry = registryClass.getField("instance").get(null);
            registryClass.getMethod("setAnimateForKey", String.class, boolean.class)
                    .invoke(registry, registeredKey, enabled);
            registryClass.getMethod("clearCache").invoke(registry);
            ReturnCapsuleMod.LOGGER.info("[humanoid] MoBends animation for {} set to {}", registeredKey, enabled);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.warn("Could not toggle MoBends animation", exception);
        }
    }

    public static boolean isNewSwing(LivingEntity entity) {
        if (!entity.swinging) {
            swingingEntities.remove(entity.getId());
            return false;
        }
        return swingingEntities.add(entity.getId());
    }

    private static final java.util.Set<Integer> swingingEntities = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isAnimating(@Nullable LivingEntity entity) {
        if (entity == null || !ModList.get().isLoaded(MOD_ID)) {
            return false;
        }
        try {
            if (registryInstance == null || getForEntity == null || isAnimated == null) {
                ClassLoader loader = MobendsAddon.class.getClassLoader();
                Class<?> registryClass = Class.forName(BENDER_REGISTRY, false, loader);
                Object instance = registryClass.getField("instance").get(null);
                if (instance == null) {
                    return false;
                }
                getForEntity = registryClass.getMethod("getForEntity", LivingEntity.class);
                isAnimated = Class.forName(BENDER, false, loader).getMethod("isAnimated");
                registryInstance = instance;
            }
            Object bender = getForEntity.invoke(registryInstance, entity);
            return bender != null && Boolean.TRUE.equals(isAnimated.invoke(bender));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    public static void driveAttack(LivingEntity entity) {
        if (!isAnimating(entity)) {
            return;
        }
        try {
            Object bender = getForEntity.invoke(registryInstance, entity);
            if (bender == null) {
                return;
            }
            Object renderer = net.minecraft.client.Minecraft.getInstance()
                    .getEntityRenderDispatcher().getRenderer(entity);
            if (renderer == null) {
                return;
            }
            if (getMutator == null || getOrMakeData == null || onAttack == null) {
                ClassLoader loader = MobendsAddon.class.getClassLoader();
                getMutator = Class.forName(BENDER, false, loader).getMethod("getMutator",
                        Class.forName("net.minecraft.client.renderer.entity.LivingEntityRenderer"));
                Class<?> mutatorClass = Class.forName(MUTATOR, false, loader);
                getOrMakeData = mutatorClass.getMethod("getOrMakeData", LivingEntity.class);

                onAttack = Class.forName(LIVING_ENTITY_DATA, false, loader)
                        .getMethod("onExternalAttack", net.minecraft.world.InteractionHand.class);
            }
            Object mutator = getMutator.invoke(bender, renderer);
            if (mutator == null) {
                return;
            }
            Object data = getOrMakeData.invoke(mutator, entity);
            if (data == null) {
                return;
            }
            onAttack.invoke(data, net.minecraft.world.InteractionHand.MAIN_HAND);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("Could not drive the MoBends attack animation: {}", exception.toString());
        }
    }

    public static void registerIfPresent() {
        if (attempted) {
            return;
        }
        attempted = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            register();
            unregisterForeignBenders(MobendsAddon.class.getClassLoader());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.warn(
                    "MoBends is installed but its addon API could not be used; humanoids keep vanilla animation",
                    exception);
        }
    }

    /**
     * Stops MoBends' own benders from animating TaCZ and Superb Warfare items, so only this addon drives the
     * humanoids. The class is looked up by name (some MoBends builds do not ship it at all) and a failure here is
     * cosmetic, hence the local catch instead of failing the whole registration.
     */
    private static void unregisterForeignBenders(ClassLoader loader) {
        try {
            Class<?> control = Class.forName(ANIMATION_CONTROL, false, loader);
            Method unregister = control.getMethod("unregister", String.class);
            unregister.invoke(null, "tacz");
            unregister.invoke(null, "superbwarfare");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.debug("MoBends animation control unavailable: {}", exception.toString());
        }
    }

    private static void register() throws ReflectiveOperationException {
        ClassLoader loader = MobendsAddon.class.getClassLoader();
        Class<?> addonsClass = Class.forName(ADDONS, false, loader);
        Class<?> iAddon = Class.forName(I_ADDON, false, loader);
        Class<?> iDataFactory = Class.forName(I_DATA_FACTORY, false, loader);
        Class<?> iMutatorFactory = Class.forName(I_MUTATOR_FACTORY, false, loader);
        Class<?> humanoidDataClass = Class.forName(HUMANOID_DATA, false, loader);
        Class<?> humanoidMutatorClass = Class.forName(HUMANOID_MUTATOR, false, loader);

        Constructor<?> dataConstructor = humanoidDataClass.getConstructor(LivingEntity.class);
        Constructor<?> mutatorConstructor = humanoidMutatorClass.getConstructor(iDataFactory);

        Object dataFactory = Proxy.newProxyInstance(loader, new Class<?>[]{iDataFactory},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createEntityData" -> dataConstructor.newInstance(args[0]);
                    case "toString" -> "returncapsule:humanoid_data_factory";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });

        Object mutatorFactory = Proxy.newProxyInstance(loader, new Class<?>[]{iMutatorFactory},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createMutator" -> mutatorConstructor.newInstance(args[0] == null ? dataFactory : args[0]);
                    case "toString" -> "returncapsule:humanoid_mutator_factory";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });

        Object addon = Proxy.newProxyInstance(loader, new Class<?>[]{iAddon},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDisplayName" -> DISPLAY_NAME;
                    case "registerContent" -> {
                        registerEntity(args[0], dataFactory, mutatorFactory, loader);
                        yield null;
                    }
                    case "toString" -> DISPLAY_NAME;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });

        Method registerAddon = addonsClass.getMethod("registerAddon", String.class, iAddon);
        registerAddon.invoke(null, ADDON_ID, addon);

        addonsClass.getMethod("flushPending").invoke(null);

        boolean present = false;
        Object registered = addonsClass.getMethod("getRegistered").invoke(null);
        if (registered instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry == addon) {
                    present = true;
                    break;
                }
            }
        }
        ReturnCapsuleMod.LOGGER.info("[humanoid] MoBends addon '{}' registered={}", ADDON_ID, present);
    }

    private static void registerEntity(Object registry, Object dataFactory, Object mutatorFactory,
                                       ClassLoader loader) throws ReflectiveOperationException {
        Class<?> iDataFactory = Class.forName(I_DATA_FACTORY, false, loader);
        Class<?> iMutatorFactory = Class.forName(I_MUTATOR_FACTORY, false, loader);
        Class<?> mutatedRenderer = Class.forName(MUTATED_RENDERER, false, loader);
        Class<?> iPreviewer = Class.forName(I_PREVIEWER, false, loader);

        Object renderer = Class.forName(BIPED_RENDERER, false, loader).getConstructor().newInstance();
        Object previewer = Class.forName(BIPED_PREVIEWER, false, loader).getConstructor().newInstance();
        String[] animations = staticTable("PLAYER_LIKE_ANIMATIONS", FALLBACK_ANIMATIONS);
        String[] parts = staticTable("BIPED_PARTS", FALLBACK_PARTS);

        Method register = registry.getClass().getMethod("registerNewEntity",
                String.class, String.class, Class.class,
                iDataFactory, iMutatorFactory, mutatedRenderer, iPreviewer,
                String[].class, String[].class);

        Object key = register.invoke(registry, ADDON_ID, "entity.returncapsule.base_humanoid", BaseHumanoid.class,
                dataFactory, mutatorFactory, renderer, previewer, animations, parts);
        registeredKey = key instanceof String name ? name : null;
        ReturnCapsuleMod.LOGGER.info("[humanoid] MoBends bender registered as {} (animations={}, parts={})",
                key, animations.length, parts.length);
        enableAnimation(key, loader);
    }

    private static void enableAnimation(Object key, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> registryClass = Class.forName(BENDER_REGISTRY, false, loader);
        Object registry = registryClass.getField("instance").get(null);
        if (registry == null) {
            return;
        }
        if (key instanceof String name) {
            registryClass.getMethod("setAnimateForKey", String.class, boolean.class)
                    .invoke(registry, name, configuredAnimate(name, loader));
        }
        registryClass.getMethod("clearCache").invoke(registry);

        Object bender = registryClass.getMethod("getForEntityClass", Class.class)
                .invoke(registry, BaseHumanoid.class);
        boolean animated = bender != null
                && Boolean.TRUE.equals(bender.getClass().getMethod("isAnimated").invoke(bender));
        ReturnCapsuleMod.LOGGER.info("[humanoid] MoBends animation active for {}: {}", key, animated);
    }

    private static boolean configuredAnimate(String key, ClassLoader loader) {
        try {
            Class<?> configClass = Class.forName(CORE_CLIENT_CONFIG, false, loader);
            Object config = configClass.getMethod("getInstance").invoke(null);
            if (config == null) {
                return true;
            }
            Object value = configClass.getMethod("isEntityAnimated", String.class).invoke(config, key);
            return !(value instanceof Boolean flag) || flag;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }
    }

    private static String[] staticTable(String fieldName, String[] fallback) {
        try {
            Class<?> addon = Class.forName(DEFAULT_ADDON, false, MobendsAddon.class.getClassLoader());
            Field field = addon.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof String[] table && table.length > 0 ? table : fallback;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("Using the built-in {} table: {}", fieldName, exception.toString());
            return fallback;
        }
    }

    private MobendsAddon() {
    }
}
