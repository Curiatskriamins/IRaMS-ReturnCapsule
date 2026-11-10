package com.curiats.returncapsule.util;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

public final class TrueDamage {
    public static final ResourceKey<DamageType> CAPSULE_IMPACT =
            ResourceKey.create(Registries.DAMAGE_TYPE, ReturnCapsuleMod.id("capsule_impact"));

    public static DamageSource source(ServerLevel level, Entity direct, @Nullable Entity owner) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CAPSULE_IMPACT);
        return new DamageSource(holder, direct, owner);
    }

    private TrueDamage() {
    }
}
