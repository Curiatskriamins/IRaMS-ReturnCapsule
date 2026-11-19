package com.curiats.returncapsule.compat.ragdoll;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.network.ModNetwork;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side glue between a downed {@link NeutralHumanoid} and Ragdollified.
 *
 * <p>Ragdollified is optional and is never bundled, so its API is reached through reflection instead of a
 * compile time dependency. Everything is bound lazily on the first call and cached; a binding failure logs
 * once and leaves the feature disabled, which is exactly the behaviour of an uninstalled mod.</p>
 */
public final class RagdollReactions {

    public static final String MOD_ID = "ragdollified";

    private static final String API_CLASS = "com.raiiiden.ragdollified.api.RagdollifiedApi";
    private static final String OPTIONS_CLASS = "com.raiiiden.ragdollified.api.SpawnOptions";
    private static final String HANDLE_CLASS = "com.raiiiden.ragdollified.api.RagdollHandle";
    private static final String STATE_CLASS = "com.raiiiden.ragdollified.api.RagdollState";

    private static final Map<Integer, Object> HANDLES = new ConcurrentHashMap<>();

    private static volatile boolean resolved;
    @Nullable
    private static volatile Api api;

    private RagdollReactions() {
    }

    public static boolean available() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void sync(NeutralHumanoid humanoid) {
        if (!available()) {
            return;
        }
        if (humanoid.isPassenger()) {
            // A passenger follows its vehicle's seat, not its own physics: no knockdown, and keep any ragdoll off.
            if (HANDLES.containsKey(humanoid.getId())) {
                end(humanoid);
            }
            return;
        }
        boolean downed = humanoid.isDownedSynced();
        boolean active = HANDLES.containsKey(humanoid.getId());
        if (downed && !active) {
            start(humanoid);
        } else if (!downed && active) {
            end(humanoid);
        } else if (downed && active && humanoid.tickCount % 5 == 0) {
            followTorso(humanoid);
        }
    }

    private static void followTorso(NeutralHumanoid humanoid) {
        Api api = api();
        if (api == null) {
            return;
        }
        Object handle = HANDLES.get(humanoid.getId());
        if (handle == null || !exists(api, handle)) {
            return;
        }
        Vec3 torso = torso(api, handle);
        if (torso != null) {
            ModNetwork.sendRagdollEnd(humanoid.getId(), torso);
        }
    }

    public static boolean start(NeutralHumanoid humanoid) {
        if (!available() || !humanoid.level().isClientSide || HANDLES.containsKey(humanoid.getId())) {
            return false;
        }
        return spawn(humanoid);
    }

    private static boolean spawn(NeutralHumanoid humanoid) {
        Api api = api();
        if (api == null) {
            return false;
        }
        try {
            Object builder = api.builder().invoke(null);
            api.hideEntity().invoke(builder, true);
            api.persistent().invoke(builder, false);
            Object options = api.build().invoke(builder);
            Object handle = api.spawn().invoke(null, humanoid, options);
            if (handle == null) {
                ReturnCapsuleMod.LOGGER.warn("ragdoll spawn returned no handle for {}",
                        humanoid.getName().getString());
                return false;
            }
            HANDLES.put(humanoid.getId(), handle);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.warn("ragdoll spawn failed, the reaction needs fixing: {}", exception.toString());
            return false;
        }
    }

    public static void end(NeutralHumanoid humanoid) {
        if (!available() || !humanoid.level().isClientSide) {
            return;
        }
        Object handle = HANDLES.remove(humanoid.getId());
        Api api = api();
        if (api == null) {
            return;
        }
        try {
            if (handle != null && exists(api, handle)) {
                Vec3 torso = torso(api, handle);
                if (torso != null) {
                    ModNetwork.sendRagdollEnd(humanoid.getId(), torso);
                }
                api.remove().invoke(null, api.entityId().invoke(handle));
                com.curiats.returncapsule.client.HumanoidImpactDetector.reset(humanoid.getId());
            }
            api.setHidden().invoke(null, humanoid.getId(), false);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.debug("ragdoll end failed: {}", exception.toString());
        }
    }

    private static boolean exists(Api api, Object handle) {
        try {
            return Boolean.TRUE.equals(api.exists().invoke(handle));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    @Nullable
    private static Vec3 torso(Api api, Object handle) {
        try {
            Object state = api.state().invoke(handle) instanceof Optional<?> optional ? optional.orElse(null) : null;
            if (state == null) {
                return null;
            }
            return api.torsoPosition().invoke(state) instanceof Vec3 position ? position : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    private static Api api() {
        if (resolved) {
            return api;
        }
        synchronized (RagdollReactions.class) {
            if (!resolved) {
                resolved = true;
                try {
                    api = Api.resolve();
                    ReturnCapsuleMod.LOGGER.info("[humanoid] Ragdollified API bound={}", api != null);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    api = null;
                    ReturnCapsuleMod.LOGGER.warn(
                            "Ragdollified is installed but its API could not be bound; humanoids keep their death animation: {}",
                            exception.toString());
                }
            }
        }
        return api;
    }

    private record Api(Method spawn, Method remove, Method setHidden,
                       Method builder, Method hideEntity, Method persistent, Method build,
                       Method exists, Method state, Method entityId, Method torsoPosition) {

        static Api resolve() throws ReflectiveOperationException {
            ClassLoader loader = RagdollReactions.class.getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, false, loader);
            Class<?> optionsClass = Class.forName(OPTIONS_CLASS, false, loader);
            Class<?> handleClass = Class.forName(HANDLE_CLASS, false, loader);
            Class<?> stateClass = Class.forName(STATE_CLASS, false, loader);

            Method builder = optionsClass.getMethod("builder");
            Class<?> builderClass = builder.getReturnType();
            return new Api(
                    find(apiClass, "spawn", NeutralHumanoid.class, optionsClass),
                    find(apiClass, "remove", int.class),
                    find(apiClass, "setEntityHidden", int.class, boolean.class),
                    builder,
                    access(builderClass.getMethod("hideEntity", boolean.class)),
                    access(builderClass.getMethod("persistent", boolean.class)),
                    access(builderClass.getMethod("build")),
                    access(handleClass.getMethod("exists")),
                    access(handleClass.getMethod("getState")),
                    access(handleClass.getMethod("entityId")),
                    access(stateClass.getMethod("torsoPosition")));
        }

        private static Method find(Class<?> owner, String name, Class<?>... arguments) throws NoSuchMethodException {
            for (Method candidate : owner.getMethods()) {
                if (matches(candidate, name, arguments)) {
                    return access(candidate);
                }
            }
            for (Method candidate : owner.getDeclaredMethods()) {
                if (matches(candidate, name, arguments)) {
                    return access(candidate);
                }
            }
            throw new NoSuchMethodException(owner.getName() + "#" + name);
        }

        private static boolean matches(Method method, String name, Class<?>[] arguments) {
            if (!method.getName().equals(name) || !Modifier.isStatic(method.getModifiers())) {
                return false;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != arguments.length) {
                return false;
            }
            for (int i = 0; i < parameters.length; i++) {
                if (!accepts(parameters[i], arguments[i])) {
                    return false;
                }
            }
            return true;
        }

        private static boolean accepts(Class<?> parameter, Class<?> argument) {
            if (parameter.isPrimitive() || argument.isPrimitive()) {
                return parameter == argument;
            }
            return parameter.isAssignableFrom(argument);
        }

        private static Method access(Method method) {
            try {
                method.setAccessible(true);
            } catch (RuntimeException ignored) {
                // public API members stay invokable without it
            }
            return method;
        }
    }
}
