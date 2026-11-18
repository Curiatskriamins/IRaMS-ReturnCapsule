package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.vehicle.VehicleRider;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID)
public final class VehicleDismountChat {

    private static final String LEAVE = "下车";
    private static final String COMBAT = "载具战斗";
    private static final String DRIVER = "主驾驶下车";
    private static final double RADIUS = 48.0D;

    private VehicleDismountChat() {
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String message = event.getMessage().getString().trim();
        if (!(event.getPlayer().level() instanceof ServerLevel level)) {
            return;
        }
        if (COMBAT.equals(message)) {
            int count = 0;
            for (NeutralHumanoid humanoid : level.getEntitiesOfClass(NeutralHumanoid.class,
                    event.getPlayer().getBoundingBox().inflate(RADIUS))) {
                humanoid.setVehicleCombat(true);
                count++;
            }
            reply(event, "[returncapsule] " + count + " 个人形进入载具战斗");
            return;
        }
        if (DRIVER.equals(message)) {
            Entity nearest = nearestVehicle(level, event.getPlayer());
            if (nearest == null) {
                reply(event, "[returncapsule] 附近没有载具");
                return;
            }
            boolean released = VehicleRider.releaseDriver(nearest);
            reply(event, released
                    ? "[returncapsule] 主驾驶已离开座位"
                    : "[returncapsule] 该载具的主驾驶不是人形");
            return;
        }
        if (LEAVE.equals(message)) {
            int count = 0;
            for (NeutralHumanoid humanoid : level.getEntitiesOfClass(NeutralHumanoid.class,
                    event.getPlayer().getBoundingBox().inflate(RADIUS))) {
                if (humanoid.getVehicle() != null || humanoid.isVehicleCombat()) {
                    VehicleRider.releaseAll(humanoid);
                    count++;
                }
            }
            reply(event, "[returncapsule] " + count + " 个人形已下车");
        }
    }

    private static Entity nearestVehicle(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        Entity best = null;
        double bestDistance = 64.0D * 64.0D;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(64.0D))) {
            if (entity instanceof net.minecraft.world.entity.vehicle.Boat || entity.getPassengers().stream()
                    .anyMatch(passenger -> passenger instanceof net.minecraft.world.entity.LivingEntity)) {
                double distance = player.distanceToSqr(entity);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = entity;
                }
            }
        }
        return best;
    }

    private static void reply(ServerChatEvent event, String text) {
        event.getPlayer().sendSystemMessage(Component.literal(text));
    }
}
