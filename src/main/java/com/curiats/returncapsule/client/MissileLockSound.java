package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps exactly one clock instance alive. A volley locks a target once per bomb and the server
 * therefore sends one lock sound packet per bomb; this handler cancels those packets and only
 * starts the clock when the previous 13.5 s clock has already finished, so the sound never
 * stacks.
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, value = Dist.CLIENT)
public final class MissileLockSound {
    private static final ResourceLocation LOCK = ReturnCapsuleMod.id("missile_lock");
    private static SoundInstance active;

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound == active) {
            return;
        }
        if (!LOCK.equals(sound.getLocation())) {
            return;
        }
        // Cancel the server-side instance; the single local clock is managed below.
        event.setSound(null);
        Minecraft minecraft = Minecraft.getInstance();
        if (active != null && minecraft.getSoundManager().isActive(active)) {
            return;
        }
        active = SimpleSoundInstance.forUI(ModSounds.MISSILE_LOCK.get(), 1.0F);
        minecraft.getSoundManager().play(active);
    }

    private MissileLockSound() {
    }
}
