package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.util.Mth;

public final class TransportAttitude {

    private static final double ENTRY_PITCH = 20.0D;

    private static final double LEVEL_START = 800.0D;

    private static final double LEVEL_BLEND = 1200.0D;

    public static float pitchDegrees(TransportCapsuleEntity entity) {
        if (!Config.mannedFallRealistic()) {
            return 0.0F;
        }
        double remaining = entity.getY() - entity.landingY();
        double blend = Mth.clamp((remaining - LEVEL_START) / LEVEL_BLEND, 0.0D, 1.0D);
        return (float) (ENTRY_PITCH * blend);
    }

    private TransportAttitude() {
    }
}
