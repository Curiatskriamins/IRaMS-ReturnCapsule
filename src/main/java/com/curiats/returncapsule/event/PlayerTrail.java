package com.curiats.returncapsule.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "returncapsule")
public final class PlayerTrail {

    private static final int MAX_POINTS = 400;
    private static final double MIN_STEP = 0.45D;

    private static final Map<UUID, Deque<Vec3>> TRAILS = new ConcurrentHashMap<>();

    private PlayerTrail() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        record(event.player);
    }

    private static void record(Player player) {
        Deque<Vec3> trail = TRAILS.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
        Vec3 last = trail.peekLast();
        Vec3 here = player.position();
        if (last != null && last.distanceToSqr(here) < MIN_STEP * MIN_STEP) {
            return;
        }
        trail.addLast(here);
        while (trail.size() > MAX_POINTS) {
            trail.removeFirst();
        }
    }

    public static List<Vec3> path(Player player) {
        Deque<Vec3> trail = TRAILS.get(player.getUUID());
        if (trail == null) {
            return List.of();
        }
        return new ArrayList<>(trail);
    }

    public static int nearestIndex(List<Vec3> path, Vec3 position) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < path.size(); index++) {
            double distance = path.get(index).distanceToSqr(position);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    public static void forget(UUID id) {
        TRAILS.remove(id);
    }
}
