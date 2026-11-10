package com.curiats.returncapsule.entity;

public final class CapsuleClientHooks {
    @FunctionalInterface
    public interface FallingTickHook {
        void onTick(FallingCapsuleEntity entity);
    }

    private static final FallingTickHook NOOP = entity -> {
    };

    private static volatile FallingTickHook fallingTick = NOOP;

    public static void install(FallingTickHook hook) {
        fallingTick = hook == null ? NOOP : hook;
    }

    public static void onFallingTick(FallingCapsuleEntity entity) {
        fallingTick.onTick(entity);
    }

    private CapsuleClientHooks() {
    }
}
