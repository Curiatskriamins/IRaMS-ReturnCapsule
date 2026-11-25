package com.curiats.returncapsule.entity.fall;

import com.curiats.returncapsule.progress.SummonTier;

/**
 * Per capsule type parameters for the shared landing path.
 *
 * <p>Only the differences live here: footprint, container slots and whether the impact deals true damage. Landing,
 * fire, particles, block placement and part structure stay in one place.</p>
 */
public enum CapsuleKind {

    /** The supply capsule: 3x3 footprint, 324 configurable slots, impact shock. */
    SUPPLY(1, 2, -1, true, false),

    /** The manned capsule: larger footprint, five slot container on the first summon only, no true damage. */
    MANNED(2, 3, 6, false, true);

    private final int radius;

    private final int height;

    private final int slots;

    private final boolean trueDamage;

    private final boolean spawnsHumanoid;

    CapsuleKind(int radius, int height, int slots, boolean trueDamage, boolean spawnsHumanoid) {
        this.radius = radius;
        this.height = height;
        this.slots = slots;
        this.trueDamage = trueDamage;
        this.spawnsHumanoid = spawnsHumanoid;
    }

    public int radius() {
        return this.radius;
    }

    public int height() {
        return this.height;
    }

    /** {@code -1} means "whatever the container was built with", i.e. the configured capsule size. */
    public int slots() {
        return this.slots;
    }

    public boolean trueDamage() {
        return this.trueDamage;
    }

    public boolean spawnsHumanoid() {
        return this.spawnsHumanoid;
    }

    public SummonTier defaultTier() {
        return SummonTier.BASIC;
    }
}
