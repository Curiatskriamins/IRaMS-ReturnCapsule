package com.curiats.returncapsule.entity;

 
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
