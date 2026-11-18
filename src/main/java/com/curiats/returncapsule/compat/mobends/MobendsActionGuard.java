package com.curiats.returncapsule.compat.mobends;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflective bridge for {@link com.curiats.returncapsule.mixin.MobendsGunLayerMixin}.
 *
 * <p>MoBends is an optional mod and is only present on the compile classpath, never in the shipped jar.
 * Everything this class needs is therefore looked up by name so the mixin itself keeps working - and keeps
 * compiling - when MoBends is not installed. {@code BipedActionController#perform} runs once per animation
 * update, so all field and method handles are cached per runtime class.</p>
 */
public final class MobendsActionGuard {

    private static final String LAYER_FIELD = "layerAction";
    private static final String ACTION_BIT_FIELD = "actionBit";
    private static final String ATTACK_TYPE_FIELD = "currentAttackActionType";
    private static final String CLEAR_ANIMATION = "clearAnimation";
    private static final String GET_ENTITY = "getEntity";

    private static final Map<Class<?>, Optional<Method>> ENTITY_GETTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Action> ACTIONS = new ConcurrentHashMap<>();

    /**
     * Reads the entity out of a MoBends {@code EntityData} without naming the type.
     */
    @Nullable
    public static LivingEntity entityOf(@Nullable Object data) {
        if (data == null) {
            return null;
        }
        try {
            Optional<Method> getter = ENTITY_GETTERS.computeIfAbsent(data.getClass(),
                    type -> Optional.ofNullable(findMethod(type, GET_ENTITY)));
            if (getter.isEmpty()) {
                return null;
            }
            return getter.get().invoke(data) instanceof LivingEntity living ? living : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("Could not read the MoBends entity data: {}", exception.toString());
            return null;
        }
    }

    /**
     * Drops the swing animation layer MoBends keeps on {@code BipedActionController}. A failure here only means
     * the humanoid keeps playing MoBends' default swing, so every lookup failure is soft.
     */
    public static void clearAction(@Nullable Object controller) {
        if (controller == null) {
            return;
        }
        Action action = ACTIONS.computeIfAbsent(controller.getClass(), Action::resolve);
        try {
            Object layer = action.layer() == null ? null : action.layer().get(controller);
            if (layer != null && action.clearAnimation() != null) {
                action.clearAnimation().invoke(layer);
            }
            if (action.actionBit() != null) {
                action.actionBit().set(controller, null);
            }
            if (action.attackType() != null) {
                action.attackType().set(controller, null);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("Could not clear the MoBends action layer: {}", exception.toString());
        }
    }

    @Nullable
    private static Method findMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    private record Action(@Nullable Field layer, @Nullable Field actionBit,
                          @Nullable Field attackType, @Nullable Method clearAnimation) {

        static Action resolve(Class<?> controller) {
            Field layer = findField(controller, LAYER_FIELD);
            Field actionBit = findField(controller, ACTION_BIT_FIELD);
            Field attackType = findField(controller, ATTACK_TYPE_FIELD);
            Method clear = layer == null ? null : findMethod(layer.getType(), CLEAR_ANIMATION);
            return new Action(layer, actionBit, attackType, clear);
        }
    }

    private MobendsActionGuard() {
    }
}
