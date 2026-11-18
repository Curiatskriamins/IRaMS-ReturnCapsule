package com.curiats.returncapsule.compat.ragdoll;

import com.curiats.returncapsule.ReturnCapsuleMod;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Reflective bridge for {@link com.curiats.returncapsule.mixin.RagdollifiedSkinLayerMixin}.
 *
 * <p>Ragdollified is optional and never shipped, so the mixin cannot take a {@code ClientRagdoll} parameter.
 * The mob type string is read by name instead; a failure only means the ragdoll keeps the humanoid model
 * rather than a player model.</p>
 */
public final class RagdollSkinSupport {

    public static final String HUMANOID_TYPE = "returncapsule:base_humanoid";

    private static final String GET_MOB_TYPE = "getMobType";

    public static boolean isHumanoidRagdoll(@Nullable Object ragdoll) {
        if (ragdoll == null) {
            return false;
        }
        try {
            Method getter = ragdoll.getClass().getMethod(GET_MOB_TYPE);
            getter.setAccessible(true);
            return HUMANOID_TYPE.equals(getter.invoke(ragdoll));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("Could not read the ragdoll mob type: {}", exception.toString());
            return false;
        }
    }

    private RagdollSkinSupport() {
    }
}
