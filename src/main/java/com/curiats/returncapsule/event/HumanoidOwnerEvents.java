package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Binds a freshly summoned base humanoid to the player who summoned it (spawn egg, command next to the player).
 *
 * <p>The manned capsule sets the owner explicitly before the entity joins, so this only fills the gap for summoning
 * methods that carry no owner information.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanoidOwnerEvents {

    private static final double ATTRIBUTION_RADIUS = 12.0D;

    private HumanoidOwnerEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof NeutralHumanoid humanoid)) {
            return;
        }
        if (humanoid.getOwnerUUID() != null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player nearest = level.getNearestPlayer(humanoid, ATTRIBUTION_RADIUS);
        if (nearest != null) {
            humanoid.setOwnerUUID(nearest.getUUID());
        }
    }

    /** Living humanoids still owned by this player, used for the three per player cap. */
    public static int ownedCount(ServerLevel level, java.util.UUID owner) {
        int count = 0;
        for (NeutralHumanoid humanoid : level.getEntitiesOfClass(NeutralHumanoid.class,
                new net.minecraft.world.phys.AABB(-3.0E7D, -2048.0D, -3.0E7D, 3.0E7D, 4096.0D, 3.0E7D))) {
            if (humanoid.isAlive() && owner.equals(humanoid.getOwnerUUID())) {
                count++;
            }
        }
        return count;
    }
}
