package com.curiats.returncapsule.entity.humanoid;

public enum WeaponKind {

    NONE,

    GUN,

    BLADE,

    BOTH;

    public boolean isGun() {
        return this == GUN;
    }

    public boolean isBlade() {
        return this == BLADE;
    }

    public boolean allowsGun() {
        return this == GUN || this == BOTH;
    }

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
