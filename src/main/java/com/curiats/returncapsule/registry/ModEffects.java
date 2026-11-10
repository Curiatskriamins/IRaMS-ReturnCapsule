package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ReturnCapsuleMod.MODID);

    
    public static final RegistryObject<MobEffect> SHAKE =
            EFFECTS.register("shake", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF) {
            });

    public static final RegistryObject<MobEffect> SHOCK =
            EFFECTS.register("shock", () -> new MobEffect(MobEffectCategory.HARMFUL, 0xAA0000) {
            });

    private ModEffects() {
    }
}
