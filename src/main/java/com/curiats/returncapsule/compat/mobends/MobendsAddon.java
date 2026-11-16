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

/**
 * Registers the humanoid with MoBends so its player-style animation set drives the model.
 * <p>
 * MoBends has a public addon API ({@code Addons.registerAddon} + {@code AddonAnimationRegistry.registerNewEntity}), but
 * it is not a compile time dependency, so everything here is reflective. That works because the two interfaces the API
 * asks for are single-method ({@code IEntityDataFactory}, {@code IMutatorFactory}, both implementable with
 * {@link Proxy}) and the rest are plain public constructors.
 * <p>
 * The registration mirrors what MoBends' own {@code DefaultAddon} does for zombies, skeletons and villagers:
 * <pre>
 *   registerNewEntity(EntityClass, dataFactory, mutatorFactory,
 *                     new BipedRenderer&lt;&gt;(), new BipedPreviewer(),
 *                     PLAYER_LIKE_ANIMATIONS, BIPED_PARTS)
 * </pre>
 * Passing a bare {@code MutatedRenderer} with an empty animation list — which is what an earlier version of this class
 * did — registers a bender that never animates anything, so the renderer, the previewer and both string tables are
 * essential. The tables are read straight off {@code DefaultAddon} so they track MoBends' own definitions.
 * <p>
 * Every failure path degrades to a warning: without MoBends, or if its internals move, the humanoid keeps the plain
 * vanilla player-model animation instead of breaking the client.
 */
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
    /** Our own mutator: the stock humanoid one mis-binds the wear sleeves of a {@code PlayerModel}. */
    private static final String HUMANOID_MUTATOR = "goblinbob.mobends.standard.mutators.HumanoidMobMutator";
    private static final String BIPED_RENDERER = "goblinbob.mobends.standard.client.renderer.entity.mutated.BipedRenderer";
    private static final String BIPED_PREVIEWER = "goblinbob.mobends.standard.previewer.BipedPreviewer";
    private static final String DEFAULT_ADDON = "goblinbob.mobends.standard.DefaultAddon";
    private static final String BENDER_REGISTRY = "goblinbob.mobends.core.bender.EntityBenderRegistry";
    private static final String BENDER = "goblinbob.mobends.core.bender.EntityBender";
    private static final String CORE_CLIENT_CONFIG = "goblinbob.mobends.core.configuration.CoreClientConfig";
    private static final String MUTATOR = "goblinbob.mobends.core.mutators.Mutator";
    private static final String LIVING_ENTITY_DATA = "goblinbob.mobends.core.data.LivingEntityData";

    /** Fallbacks copied from MoBends' DefaultAddon, used only if its static tables cannot be read. */
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
    /** Key returned by MoBends for our bender, used to toggle its animation at runtime. */
    private static volatile String registeredKey;

    /**
     * Whether MoBends is currently driving this entity's model, i.e. it has a registered bender and the bender's
     * animation switch is on. Used to arbitrate against other animation systems that write the same model.
     */
    /**
     * Turns MoBends animation for our humanoids on or off at runtime.
     * <p>
     * This exists so the two hypotheses can be separated in game: with MoBends switched off the entity uses the plain
     * vanilla player model. If the mirrored hands and the missing attack swing disappear, the fault is in MoBends'
     * mutation of our model; if they stay, it is in how the entity is registered or rendered.
     */
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

    /**
     * True when this entity has started a new swing since the last call.
     * <p>
     * Edge detection on the {@code swinging} flag: it is true for the whole swing (about six ticks), and the client tick
     * runs twenty times a second, so a swing cannot be missed. Comparing {@code swingTime} values instead relied on the
     * exact numbers observed at whatever moment the caller happened to run, which is why most swings went unnoticed.
     */
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

    /**
     * Feeds one attack into MoBends' animation state for this entity.
     * <p>
     * MoBends only ever calls {@code onAttack()} from its player path and from an optional vanilla-backport compat;
     * for a modded mob nothing fires it, so the biped controller never learns that a swing happened and the arm stays
     * still while the sword simply changes orientation. Everything needed to do it by hand is public: the bender
     * exposes {@code getMutator(renderer)} and the mutator exposes {@code getOrMakeData(entity)}, which hands back the
     * {@code LivingEntityData} holding {@code onAttack()}.
     * <p>
     * Called once per swing, from the renderer, guarded by {@code swingTime == 1} so a single swing is counted once.
     */
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
                // Hand-aware entry point: it flags the attack as externally driven and routes to the main hand, which is
                // what the biped controller reads. The no-argument onAttack() left the swing on the wrong arm.
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

    /** Called once from client setup. Does nothing when MoBends is not installed. */
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
            // The gun mods ship MoBends animation packs, and a pack drives the arm bones itself — anything written after
            // it still loses, which is why writing the pose in three different places changed nothing on screen. This is
            // exactly what the standalone gun-fix mod does: drop those packs so the pose written by this mod is the last
            // word. Unregistering a name that was never registered is harmless.
            goblinbob.mobends.api.animation.MoBendsAnimationControl.unregister("tacz");
            goblinbob.mobends.api.animation.MoBendsAnimationControl.unregister("superbwarfare");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.warn(
                    "MoBends is installed but its addon API could not be used; humanoids keep vanilla animation",
                    exception);
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

        // MoBends may have already flushed its queue during its own client setup, so flush again.
        addonsClass.getMethod("flushPending").invoke(null);

        // Verify the registration actually stuck; "registered" alone does not prove MoBends kept it.
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

    /** One {@code registerNewEntity} call with the same shape MoBends uses for its own humanoid mobs. */
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
        // An explicit key is mandatory: without it MoBends derives the EntityType from class.getSimpleName() and
        // throws when the registry path does not happen to match.
        Object key = register.invoke(registry, ADDON_ID, "entity.returncapsule.base_humanoid", BaseHumanoid.class,
                dataFactory, mutatorFactory, renderer, previewer, animations, parts);
        registeredKey = key instanceof String name ? name : null;
        ReturnCapsuleMod.LOGGER.info("[humanoid] MoBends bender registered as {} (animations={}, parts={})",
                key, animations.length, parts.length);
        enableAnimation(key, loader);
    }

    /**
     * Turns animation on for the freshly registered bender.
     * <p>
     * Registering only creates the bender; whether it animates is a per-key flag owned by
     * {@code EntityBenderRegistry}, and a key that MoBends' own config never mentioned stays off. MoBends applies that
     * configuration once during its client setup, which may run before or after this registration, so the addon has to
     * repeat what {@code BenderDiscovery} does: read the config entry (defaulting to enabled) and set the flag.
     * <p>
     * This mirrors the CNPC/MoBends compatibility mod, which registers player-model humanoids through the same API.
     */
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

    /** MoBends' own per-key switch, defaulting to enabled when no config entry exists. */
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

    /** Reads one of MoBends' own {@code String[]} tables so we do not drift from its definitions. */
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
