package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.network.ModNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TransportClient {
    private static CameraType previousCamera;
    private static boolean wasUseDown;
    private static boolean wasRiding;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean riding = player != null && player.getVehicle() instanceof TransportCapsuleEntity;

        boolean useDown = minecraft.options.keyUse.isDown();
        if (riding && !wasRiding) {
            
            
            wasUseDown = useDown;
        } else if (riding && useDown && !wasUseDown && minecraft.screen == null) {
            ModNetwork.openTransportMenu(((TransportCapsuleEntity) player.getVehicle()).getId());
        }
        wasUseDown = useDown;
        wasRiding = riding;

        if (riding) {
            if (previousCamera == null) {
                previousCamera = minecraft.options.getCameraType();
            }
            if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (previousCamera != null) {
            minecraft.options.setCameraType(previousCamera);
            previousCamera = null;
        }
    }

    private TransportClient() {
    }
}
