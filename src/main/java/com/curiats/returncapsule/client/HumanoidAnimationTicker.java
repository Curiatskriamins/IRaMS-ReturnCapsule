package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.mobends.MobendsAddon;
import com.curiats.returncapsule.entity.humanoid.BaseHumanoid;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HumanoidAnimationTicker {

    private static final double SCAN_RADIUS = 64.0D;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        for (BaseHumanoid humanoid : minecraft.level.getEntitiesOfClass(
                BaseHumanoid.class, minecraft.player.getBoundingBox().inflate(SCAN_RADIUS))) {

            int sequence = humanoid.attackSequence();
            Integer previous = lastSequence.put(humanoid.getId(), sequence);
            if (previous == null) {

                ReturnCapsuleMod.LOGGER.debug("[humanoid] animation ticker tracking entity {} (sequence={})",
                        humanoid.getId(), sequence);
            }
            if (previous == null ? sequence > 0 : sequence != previous) {
                MobendsAddon.driveAttack(humanoid);
            }
            com.curiats.returncapsule.compat.ragdoll.RagdollReactions.sync(humanoid);
            HumanoidImpactDetector.tick(humanoid);
        }
    }

    private static final java.util.Map<Integer, Integer> lastSequence = new java.util.concurrent.ConcurrentHashMap<>();

    private HumanoidAnimationTicker() {
    }
}
