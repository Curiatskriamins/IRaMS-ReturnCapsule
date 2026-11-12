package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.item.KillMarkerItem;
import com.curiats.returncapsule.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, value = Dist.CLIENT)
public final class MissileLockSound {
    private static final ResourceLocation LOCK = ReturnCapsuleMod.id("missile_lock");
    private static SoundInstance active;

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }
        if (!LOCK.equals(sound.getLocation())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (active != null && minecraft.getSoundManager().isActive(active)) {
            event.setSound(null);
            return;
        }
        active = sound;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof KillMarkerItem)) {
            return;
        }
        if (active != null && minecraft.getSoundManager().isActive(active)) {
            return;
        }
        active = SimpleSoundInstance.forUI(ModSounds.MISSILE_LOCK.get(), 1.0F);
        minecraft.getSoundManager().play(active);
    }

    private MissileLockSound() {
    }
}
