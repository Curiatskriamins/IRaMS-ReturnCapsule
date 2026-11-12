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
    private static final ResourceLocation BOOM = ReturnCapsuleMod.id("boom_impact");
    private static SoundInstance active;
    private static boolean ending;

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (BOOM.equals(sound.getLocation())) {
            if (active == null && !ending) {
                return;
            }
            if (active != null) {
                minecraft.getSoundManager().stop(active);
                active = null;
            }
            ending = true;
            active = SimpleSoundInstance.forUI(ModSounds.MISSILE_LOCK_END.get(), 1.0F);
            minecraft.getSoundManager().play(active);
            ReturnCapsuleMod.LOGGER.info("[sound] missile_lock ending on explosion");
            return;
        }
        if (!LOCK.equals(sound.getLocation())) {
            return;
        }
        if (ending) {
            event.setSound(null);
            return;
        }
        if (active != null && minecraft.getSoundManager().isActive(active)) {
            event.setSound(null);
            ReturnCapsuleMod.LOGGER.info("[sound] missile_lock suppressed (active)");
            return;
        }
        active = sound;
        ReturnCapsuleMod.LOGGER.info("[sound] missile_lock accepted {}", sound.getLocation());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (ending) {
            if (active == null || !minecraft.getSoundManager().isActive(active)) {
                ending = false;
                active = null;
            }
            return;
        }
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
        ReturnCapsuleMod.LOGGER.info("[sound] missile_lock replay");
    }

    private MissileLockSound() {
    }
}
