package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
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
    /** Every tick of the chase deals this much magic damage; small on purpose, so SRP caps never clip it. */
    public static final float PURSUIT_TICK_DAMAGE = 3.0F;

    /** How long one swing keeps chasing, in ticks. */
    public static final int PURSUIT_DURATION_TICKS = 60;

    /** Only targets above this max health are pursued. */
    public static final double PURSUIT_MIN_HEALTH = 150.0D;

    public static final RegistryObject<MobEffect> PURSUIT =
            EFFECTS.register("pursuit", () -> new MobEffect(MobEffectCategory.HARMFUL, 0xB31414) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return true;
                }

                @Override
                public void applyEffectTick(LivingEntity entity, int amplifier) {
                    if (entity.level().isClientSide || entity.isDeadOrDying()) {
                        return;
                    }
                    if (entity.getMaxHealth() <= PURSUIT_MIN_HEALTH) {
                        return;
                    }
                    // Same shape as the mimic armory's gaping effect: a small magic hit every tick. Magic is what makes
                    // it slip past SRP's damage cap and adaptation, and a small per tick value is what keeps each tick
                    // under that cap instead of losing the excess.
                    float damage = PURSUIT_TICK_DAMAGE;
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
