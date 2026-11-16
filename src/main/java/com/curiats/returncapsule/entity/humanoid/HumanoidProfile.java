package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.resources.ResourceLocation;

public enum HumanoidProfile {
    BASE("base", 200.0D, 50.0D, 0.50D, 5.0D, WeaponKind.BOTH);

    public static final double WALK_SPEED = 0.3D;

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

    public ResourceLocation skin() {
        return ReturnCapsuleMod.id("textures/entity/humanoid/" + this.id + ".png");
    }

    public String descriptionKey() {
        return "entity.returncapsule." + this.id + "_humanoid";
    }
}
