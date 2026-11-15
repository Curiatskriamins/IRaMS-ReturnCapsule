package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.mobends.MobendsAddon;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Arbitrates third-person animation between TaCZ's PlayerAnimator layer and MoBends, per animation type.
 * <p>
 * Both systems write the same {@code PlayerModel} parts, and whichever runs last wins, so they cannot both be active:
 * <ul>
 *   <li><b>Firing / idle</b> — MoBends owns the body. TaCZ's layer is told there is no animation to play, otherwise its
 *       upper-body overlay fights the MoBends biped animation for the arms.</li>
 *   <li><b>Reloading</b> — TaCZ owns it. A reload is a long, readable action with its own hand movements, and cutting it
 *       off left the animation flashing for a moment and then disappearing. So while a reload is in progress the gate
 *       stays open.</li>
 * </ul>
 * {@code @Pseudo} plus {@code require = 0} keep the whole thing harmless when TaCZ is absent or moves the method.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat", remap = false)
public class TaczPlayerAnimatorMixin {
    private static final String RELOADING = "NOT_RELOADING";

    private static volatile Method fromLivingEntity;
    private static volatile Method getSynReloadState;
    private static volatile Method getStateType;

    /**
     * Gates every TaCZ third-person animation for this entity. Returning {@code false} means "nothing to play", which
     * leaves the model to MoBends.
     */
    @Inject(method = "canPlayAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$arbitrate(LivingEntity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!MobendsAddon.isAnimating(entity)) {
            return;
        }
        // A reload runs to completion; everything else is MoBends' to animate.
        if (isReloading(entity)) {
            return;
        }
        callback.setReturnValue(false);
    }

    /** True while TaCZ reports a reload in progress for this entity. */
    private static boolean isReloading(LivingEntity entity) {
        try {
            if (fromLivingEntity == null) {
                ClassLoader loader = TaczPlayerAnimatorMixin.class.getClassLoader();
                Class<?> operator = Class.forName("com.tacz.guns.api.entity.IGunOperator", false, loader);
                fromLivingEntity = operator.getMethod("fromLivingEntity", LivingEntity.class);
                getSynReloadState = operator.getMethod("getSynReloadState");
                getStateType = Class.forName("com.tacz.guns.api.entity.ReloadState", false, loader)
                        .getMethod("getStateType");
            }
            Object handle = fromLivingEntity.invoke(null, entity);
            if (handle == null) {
                return false;
            }
            Object state = getSynReloadState.invoke(handle);
            if (state == null) {
                return false;
            }
            Object type = getStateType.invoke(state);
            return type instanceof Enum<?> value && !RELOADING.equals(value.name());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }
}
