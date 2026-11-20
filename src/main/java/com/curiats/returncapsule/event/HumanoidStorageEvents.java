package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.util.HumanoidStorage;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps the invisible humanoid store with the player across respawn, like an ender chest does. */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanoidStorageEvents {

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        HumanoidStorage.copy(event.getOriginal(), event.getEntity());
    }

    private HumanoidStorageEvents() {
    }
}
