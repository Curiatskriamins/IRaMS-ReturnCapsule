package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ReturnCapsuleMod.MODID);

    /**
     * Landing thud. The default sounds.json maps it to a slowed anvil; the bundled
     * {@code IRaMS_titan_fall} resource pack overrides it with the titan fall sound.
     */
    public static final RegistryObject<SoundEvent> CAPSULE_IMPACT = SOUNDS.register("capsule_impact",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("capsule_impact")));

    /** Hatch/air sound played when the return capsule starts opening. */
    public static final RegistryObject<SoundEvent> CAPSULE_OPEN = SOUNDS.register("capsule_open",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("capsule_open")));

    /** Clock sound played when a cruise bomb locks onto a living target. */
    public static final RegistryObject<SoundEvent> MISSILE_LOCK = SOUNDS.register("missile_lock",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("missile_lock")));

    /** Explosion sound for the cruise bomb impact. */
    public static final RegistryObject<SoundEvent> BOOM_IMPACT = SOUNDS.register("boom_impact",
            () -> SoundEvent.createVariableRangeEvent(ReturnCapsuleMod.id("boom_impact")));

    private ModSounds() {
    }
}
