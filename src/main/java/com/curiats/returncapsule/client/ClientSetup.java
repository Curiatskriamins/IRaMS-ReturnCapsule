package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomClientHooks;
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
        event.registerEntityRenderer(ModEntities.BASE_HUMANOID.get(), HumanoidRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CAPSULE.get(), CapsuleBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MANNED_CAPSULE.get(), CapsuleBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TRANSPORT_MARKER.get(), TransportMarkerRenderer::new);
    }


    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.CAPSULE.get(), CapsuleScreen::new);
            net.minecraft.client.renderer.item.ItemProperties.register(
                    com.curiats.returncapsule.registry.ModItems.STANDARD_STORAGE_ANCHOR.get(),
                    com.curiats.returncapsule.ReturnCapsuleMod.id("stored"),
                    (stack, level, entity, seed) -> com.curiats.returncapsule.util.AnchorStorage.count(stack));

            CapsuleClientHooks.install(ClientTrail::onFallingTick);
            BoomClientHooks.install(BoomClientTrail::onTick);
            ReturnCapsuleMod.LOGGER.debug("[kill-marker] renderer registered");
            com.curiats.returncapsule.compat.mobends.MobendsAddon.registerIfPresent();
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
                MannedCapsuleGeoModel.reload();
                BoomGeoModel.reload();
            }
        });
    }
}
