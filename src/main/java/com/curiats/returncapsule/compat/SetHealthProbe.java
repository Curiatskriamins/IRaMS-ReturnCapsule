package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;

/**
 * Liveness probe for the {@code LivingEntity.setHealth} mixin.
 *
 * <p>Distinguishes the two ways the damage conversion can look dead: the inject never applied (no probe line at all),
 * or it applied but the parasite mod never writes health directly (probe lines but no conversion lines). Without this,
 * the two cases are indistinguishable in a log.</p>
 */
public final class SetHealthProbe {

    private static final int LOG_EVERY = 2400;

    private static int calls;

    public static void seen() {
        if (++calls % LOG_EVERY == 0) {
            ReturnCapsuleMod.LOGGER.info("[humanoid] setHealth hook alive ({} calls)", calls);
        }
    }

    private SetHealthProbe() {
    }
}
