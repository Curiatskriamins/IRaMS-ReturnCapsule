package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.vehicle.VehicleAmmoGate;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Removes frozen vehicle ammunition that has outlived its configured deadline.
 *
 * <p>The sweep runs every {@link #SWEEP_INTERVAL} ticks, not every tick: the deadline being ten minutes long costs
 * nothing by itself, only the checking frequency does. With a handful of frozen rounds per engagement the sweep is a
 * few map lookups, and when nothing is frozen it returns immediately.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VehicleAmmoReaper {

    private static final int SWEEP_INTERVAL = 200;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % SWEEP_INTERVAL != 0) {
            return;
        }
        VehicleAmmoGate.reap(server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VehicleAmmoGate.clear();
    }

    private VehicleAmmoReaper() {
    }
}
