package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ReturnCapsuleMod.MODID);

     
    public static final RegistryObject<SoundEvent> CAPSULE_IMPACT = SOUNDS.register("capsule_impact",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("capsule_impact")));

     
    public static final RegistryObject<SoundEvent> CAPSULE_OPEN = SOUNDS.register("capsule_open",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("capsule_open")));

     
    public static final RegistryObject<SoundEvent> MISSILE_LOCK = SOUNDS.register("missile_lock",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("missile_lock")));

    public static final RegistryObject<SoundEvent> MISSILE_LOCK_END = SOUNDS.register("missile_lock_end",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("missile_lock_end")));

     
    public static final RegistryObject<SoundEvent> BOOM_IMPACT = SOUNDS.register("boom_impact",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("boom_impact")));

    private ModSounds() {
    }
}
