package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.tacz.TaczCatalog;
import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.curiats.returncapsule.entity.humanoid.HumanoidSquad;
import com.curiats.returncapsule.entity.humanoid.HumanoidWeapons;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerState {
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ShockManager.clear();
        LrSupport.invalidate();
        TaczCatalog.invalidate();
        TaczRifles.invalidate();
        HumanoidWeapons.invalidate();
        HumanoidSquad.clear();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        TaczCatalog.invalidate();
        TaczRifles.invalidate();
        LrSupport.invalidate();
        HumanoidWeapons.invalidate();
    }

    private ServerState() {
    }
}
