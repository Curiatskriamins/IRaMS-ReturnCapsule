package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.tacz.TaczCatalog;
import com.curiats.returncapsule.util.HiddenPlayers;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerState {
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ShockManager.clear();
        HiddenPlayers.clear();
        LrSupport.invalidate();
        TaczCatalog.invalidate();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        
        TaczCatalog.invalidate();
        LrSupport.invalidate();
    }

    private ServerState() {
    }
}
