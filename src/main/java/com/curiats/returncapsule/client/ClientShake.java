package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;








@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientShake {
    private static final int SHAKE_TICKS = 25;

    private static int soundTicks;
    private static float soundAmplitude;
    private static SoundInstance lastRumble;

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        float maxAmplitude = Config.SHAKE_AMPLITUDE.get().floatValue();
        float effectAmplitude = 0.0F;
        MobEffectInstance effect = player.getEffect(ModEffects.SHAKE.get());
        if (effect != null) {
            float base = Math.min(maxAmplitude, 1.8F + effect.getAmplifier() * 1.4F);
            effectAmplitude = base * Math.min(1.0F, effect.getDuration() / 8.0F);
        }
        float soundAmplitudeNow = soundTicks > 0
                ? soundAmplitude * (soundTicks / (float) SHAKE_TICKS)
                : 0.0F;
        float amplitude = Math.max(effectAmplitude, soundAmplitudeNow);
        if (amplitude <= 0.0F) {
            return;
        }

        float t = player.tickCount * 1.7F;
        event.setYaw(event.getYaw() + Mth.sin(t * 13.0F) * amplitude);
        event.setPitch(event.getPitch() + Mth.cos(t * 17.0F) * amplitude * 0.6F);
        event.setRoll(event.getRoll() + Mth.sin(t * 11.0F) * amplitude * 0.45F);
    }

    



    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }
        float volume;
        try {
            
            if (sound.getSound() == null) {
                return;
            }
            volume = sound.getVolume();
        } catch (RuntimeException ignored) {
            return;
        }
        if (!sound.getLocation().equals(SoundEvents.GENERIC_EXPLODE.getLocation())) {
            return;
        }

        
        
        Minecraft minecraft = Minecraft.getInstance();
        if (lastRumble != null) {
            minecraft.getSoundManager().stop(lastRumble);
            lastRumble = null;
        }

        if (volume < 1.0F) {
            lastRumble = sound;
            return;
        }

        if (!Config.SCREEN_SHAKE.get()) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        double dx = sound.getX() - player.getX();
        double dy = sound.getY() - player.getY();
        double dz = sound.getZ() - player.getZ();
        double range = Config.SHAKE_SOUND_RANGE.get();
        if (dx * dx + dy * dy + dz * dz <= range * range) {
            soundTicks = SHAKE_TICKS;
            soundAmplitude = Config.SHAKE_AMPLITUDE.get().floatValue();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && soundTicks > 0) {
            soundTicks--;
        }
    }

    private ClientShake() {
    }
}
