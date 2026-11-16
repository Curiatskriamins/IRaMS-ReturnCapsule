package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.mobends.MobendsAddon;
import com.curiats.returncapsule.entity.humanoid.BaseHumanoid;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the MoBends attack animation from the client tick rather than from the renderer.
 * <p>
 * A swing only lasts a handful of ticks, and the renderer is not guaranteed to be called during them — an off-screen
 * entity, or one rendered through another path, simply misses the event. Ticking is the reliable place: it runs every
 * tick for every loaded entity regardless of what is on screen.
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HumanoidAnimationTicker {
    /** Radius around the player to scan; generous enough to cover any humanoid that can be seen. */
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
            // The sequence number is bumped server side on every swing and synched, so this cannot miss an attack the way
            // the client's own swing flags did.
            int sequence = humanoid.attackSequence();
            Integer previous = lastSequence.put(humanoid.getId(), sequence);
            if (previous == null) {
                // Proof that the ticker runs and that the synched counter arrived with the entity.
                ReturnCapsuleMod.LOGGER.info("[humanoid] animation ticker tracking entity {} (sequence={})",
                        humanoid.getId(), sequence);
            }
            if (previous == null ? sequence > 0 : sequence != previous) {
                MobendsAddon.driveAttack(humanoid);
            }
        }
    }

    private static final java.util.Map<Integer, Integer> lastSequence = new java.util.concurrent.ConcurrentHashMap<>();

    private HumanoidAnimationTicker() {
    }
}
