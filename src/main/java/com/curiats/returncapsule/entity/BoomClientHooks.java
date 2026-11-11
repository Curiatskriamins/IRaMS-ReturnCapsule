package com.curiats.returncapsule.entity;

/**
 * Client-only hook for the cruise bomb, mirroring {@link CapsuleClientHooks}. Keeping the
 * reference as an interface means the dedicated server never loads the particle-only client
 * class.
 */
public final class BoomClientHooks {
    @FunctionalInterface
    public interface TrailHook {
        void onTick(BoomEntity entity);
    }

    private static final TrailHook NOOP = entity -> {
    };

    private static volatile TrailHook trail = NOOP;

    public static void install(TrailHook hook) {
        trail = hook == null ? NOOP : hook;
    }

    public static void onTick(BoomEntity entity) {
        trail.onTick(entity);
    }

    private BoomClientHooks() {
    }
}
