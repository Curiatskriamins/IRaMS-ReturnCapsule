package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.util.TrueDamage;
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

    /**
     * The blade's pursuit, modelled on the mimic armory's red mark: a per tick write that clears invulnerability frames
     * before every instance and falls back to a direct health write when the hit is dropped anyway.
     */
    public static final RegistryObject<MobEffect> PURSUIT =
            EFFECTS.register("pursuit", () -> new MobEffect(MobEffectCategory.HARMFUL, 0xB31414) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return duration % com.curiats.returncapsule.Config.EQUIPMENT_PURSUIT_INTERVAL_TICKS.get() == 0;
                }

                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity.level().isClientSide || entity.isDeadOrDying()) {
                        return;
                    }
                    if (entity.getMaxHealth() <= Config.EQUIPMENT_PURSUIT_MIN_HEALTH.get()) {
                        return;
                    }
                    // Same shape as the mimic armory's gaping effect: a small magic hit every tick. Magic is what makes
                    // it slip past SRP's damage cap and adaptation, and a small per tick value is what keeps each tick
                    // under that cap instead of losing the excess.
                    float damage = Config.EQUIPMENT_PURSUIT_TICK_DAMAGE.get().floatValue();
                    entity.invulnerableTime = 0;
                    float before = entity.getHealth();
                    net.minecraft.world.damagesource.DamageSource source = entity.damageSources().magic();
                    entity.hurt(source, damage);
                    if (entity.getHealth() >= before) {
                        entity.setHealth(Math.max(0.0F, before - damage));
                    }
                }
            });


    private ModEffects() {
    }
}
