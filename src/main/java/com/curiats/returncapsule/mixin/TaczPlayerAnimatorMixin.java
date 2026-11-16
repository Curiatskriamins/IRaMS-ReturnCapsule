package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.mobends.MobendsAddon;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat", remap = false)
public class TaczPlayerAnimatorMixin {
    private static final String RELOADING = "NOT_RELOADING";

    private static volatile Method fromLivingEntity;
    private static volatile Method getSynReloadState;
    private static volatile Method getStateType;

    @Inject(method = "canPlayAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$arbitrate(LivingEntity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!MobendsAddon.isAnimating(entity)) {
            return;
        }

        if (isReloading(entity)) {
            return;
        }
        callback.setReturnValue(false);
    }

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

            if (!(type instanceof Enum<?> value)) {
                return false;
            }
            String stateName = value.name();
            return "START".equals(stateName) || "LOOP".equals(stateName);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }
}
