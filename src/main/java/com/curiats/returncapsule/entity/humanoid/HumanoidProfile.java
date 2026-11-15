package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.resources.ResourceLocation;

/**
 * The neutral humanoid. It carries the player model, the player hitbox and the player's feel for movement, and it can
 * use both a gun and a blade.
 */
public enum HumanoidProfile {
    BASE("base", 200.0D, 50.0D, 0.50D, 5.0D, WeaponKind.BOTH);

    /**
     * Walking speed used by the pathfinder. Deliberately not the player's {@code 0.1} attribute value: a mob feeds the
     * attribute straight into path following while a player's movement is scaled differently, so {@code 0.1} here
     * produced a crawl. {@code 0.3} reads as a player's walking pace in game (a zombie runs at {@code 0.23}).
     */
    public static final double WALK_SPEED = 0.3D;
    /** Vanilla player attack speed, so the arm swing matches a player's. */
    public static final double PLAYER_ATTACK_SPEED = 4.0D;

    private final String id;
    private final double maxHealth;
    private final double armor;
    private final double damageReduction;
    private final double regenPerSecond;
    private final WeaponKind weapons;

    HumanoidProfile(String id, double maxHealth, double armor, double damageReduction,
                    double regenPerSecond, WeaponKind weapons) {
        this.id = id;
        this.maxHealth = maxHealth;
        this.armor = armor;
        this.damageReduction = damageReduction;
        this.regenPerSecond = regenPerSecond;
        this.weapons = weapons;
    }

    public String id() {
        return this.id;
    }

    public double maxHealth() {
        return this.maxHealth;
    }

    public double armor() {
        return this.armor;
    }

    /** Flat fraction of every incoming hit that is removed before armour is applied. */
    public double damageReduction() {
        return this.damageReduction;
    }

    public double regenPerSecond() {
        return this.regenPerSecond;
    }

    public WeaponKind weapons() {
        return this.weapons;
    }

    public boolean allows(WeaponKind kind) {
        return this.weapons.allows(kind);
    }

    /** The 64x64 player skin shipped with the mod, drawn with the classic (4px arm) player model. */
    public ResourceLocation skin() {
        return ReturnCapsuleMod.id("textures/entity/humanoid/" + this.id + ".png");
    }

    public String descriptionKey() {
        return "entity.returncapsule." + this.id + "_humanoid";
    }
}
