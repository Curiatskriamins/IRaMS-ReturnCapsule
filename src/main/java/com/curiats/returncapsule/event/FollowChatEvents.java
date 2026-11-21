package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.vehicle.VehicleDriverCompat;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Chat switch used while tuning the vehicle AI.
 *
 * <p>跟随 turns on straight line following (no route, no corridor, no recovery), 停止跟随 turns it off. Both are silent
 * toggles rather than commands so a player can flip them mid test.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FollowChatEvents {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String message = event.getMessage().getString().trim();
        if ("跟随".equals(message)) {
            VehicleDriverCompat.setStraightFollow(true);
            event.getPlayer().displayClientMessage(Component.literal("[列装] 直线跟随：开"), true);
            ReturnCapsuleMod.LOGGER.info("[drive] straight follow enabled by {}", event.getPlayer().getName().getString());
        } else if ("停止跟随".equals(message) || "取消跟随".equals(message)) {
            VehicleDriverCompat.setStraightFollow(false);
            event.getPlayer().displayClientMessage(Component.literal("[列装] 直线跟随：关"), true);
            ReturnCapsuleMod.LOGGER.info("[drive] straight follow disabled by {}", event.getPlayer().getName().getString());
        }
    }

    private FollowChatEvents() {
    }
}
