package com.curiats.returncapsule.util;

import com.curiats.returncapsule.block.CapsuleBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HiddenPlayers {
    private static final Map<UUID, CapsuleBlockEntity> HIDDEN = new HashMap<>();

    public static void put(UUID playerId, CapsuleBlockEntity capsule) {
        HIDDEN.put(playerId, capsule);
    }

    public static void remove(UUID playerId) {
        HIDDEN.remove(playerId);
    }

    public static boolean isHidden(Player player) {
        return player instanceof ServerPlayer && HIDDEN.containsKey(player.getUUID());
    }

    public static boolean isHidden(UUID playerId) {
        return HIDDEN.containsKey(playerId);
    }

    public static void clear() {
        HIDDEN.clear();
    }

    private HiddenPlayers() {
    }
}
