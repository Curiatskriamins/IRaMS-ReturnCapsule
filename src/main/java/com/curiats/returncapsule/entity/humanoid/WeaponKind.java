package com.curiats.returncapsule.entity.humanoid;

/**
 * Where a weapon sits for a humanoid: what it is, and which of the two combat styles a profile allows.
 */
public enum WeaponKind {
    /** Nothing this mod recognises, or an empty hand. */
    NONE,
    /** A TaCZ gun (any pack). */
    GUN,
    /** A slashblade. */
    BLADE,
    /** Not a kind of its own: the profile accepts both, see {@link HumanoidProfile}. */
    BOTH;

    public boolean isGun() {
        return this == GUN;
    }

    public boolean isBlade() {
        return this == BLADE;
    }

    /** Whether a profile restricted to {@code this} accepts a gun. */
    public boolean allowsGun() {
        return this == GUN || this == BOTH;
    }

    /** Whether a profile restricted to {@code this} accepts a blade. */
    public boolean allowsBlade() {
        return this == BLADE || this == BOTH;
    }

    public boolean allows(WeaponKind kind) {
        return switch (kind) {
            case GUN -> allowsGun();
            case BLADE -> allowsBlade();
            case BOTH -> allowsGun() && allowsBlade();
            case NONE -> false;
        };
    }
}
