package com.curiats.returncapsule.progress;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.resources.ResourceLocation;

public enum SummonTier {
    BASIC("basic", "chests/supply_capsule"),
    ADVANCED("advanced", "chests/supply_capsule_advanced"),
    ELITE("elite", "chests/supply_capsule_elite");

    private final String id;
    private final ResourceLocation lootTable;

    SummonTier(String id, String lootTable) {
        this.id = id;
        this.lootTable = ReturnCapsuleMod.id(lootTable);
    }

    public String id() {
        return this.id;
    }

    public ResourceLocation lootTable() {
        return this.lootTable;
    }

    public String containerKey() {
        return "container.returncapsule.capsule." + this.id;
    }

    public String transportKey() {
        return "container.returncapsule.transport." + this.id;
    }

    public static SummonTier byName(String name) {
        for (SummonTier tier : values()) {
            if (tier.id.equals(name)) {
                return tier;
            }
        }
        return BASIC;
    }
}
