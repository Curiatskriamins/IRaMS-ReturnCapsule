package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.CapsuleClientHooks;
import com.curiats.returncapsule.registry.ModBlockEntities;
import com.curiats.returncapsule.registry.ModEntities;
import com.curiats.returncapsule.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SUPPLY_CAPSULE.get(), CapsuleRenderer::new);
        event.registerEntityRenderer(ModEntities.TRANSPORT_CAPSULE.get(), TransportCapsuleRenderer::new);
        event.registerEntityRenderer(ModEntities.BOOM.get(), BoomRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CAPSULE.get(), CapsuleBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TRANSPORT_MARKER.get(), TransportMarkerRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.CAPSULE.get(), CapsuleScreen::new);
            // Common code calls this hook instead of allocating a DistExecutor lambda per tick.
            CapsuleClientHooks.install(ClientTrail::onFallingTick);
        });
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler) {
                CapsuleGeoModel.reload();
                BoomGeoModel.reload();
            }
        });
    }
}
