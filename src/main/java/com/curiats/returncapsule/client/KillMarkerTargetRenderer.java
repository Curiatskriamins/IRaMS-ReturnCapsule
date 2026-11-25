package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The kill marker is drawn by the locked target's own render event, not by the bomb's, and it uses the same billboard
 * helper as the flare but with the front layer so the target model cannot cover it.
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KillMarkerTargetRenderer {

    private static final ResourceLocation MARKER = ReturnCapsuleMod.id("textures/entity/kill_ssag.png");

    private KillMarkerTargetRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity target = event.getEntity();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !isLockedByBomb(minecraft, target)) {
            return;
        }
        Flare.Spec spec = new Flare.Spec(MARKER, target.getBbHeight() + 1.5D, 0.1F, 0.5F, 64.0F,
                Flare.Layer.FRONT);
        Flare.render(target, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), spec);
    }

    private static boolean isLockedByBomb(Minecraft minecraft, LivingEntity target) {
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof BoomEntity bomb && bomb.clientLockedEntity() == target.getId()) {
                return true;
            }
        }
        return false;
    }
}
