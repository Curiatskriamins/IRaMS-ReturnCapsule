package com.curiats.returncapsule.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Pet checks shared by the standard issue blade and the humanoid's percentage damage.
 *
 * <p>Pets are player property: the blade must never shrink their max health, and a humanoid's percentage strip has no
 * business touching them either. Ownership is read through {@link OwnableEntity} so tamed animals, horses and modded
 * pets all answer the same way.</p>
 */
public final class Pets {

    public static boolean isPet(LivingEntity entity) {
        return entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
    }

    public static boolean isOwnedBy(LivingEntity entity, Player player) {
        return entity instanceof OwnableEntity ownable
                && ownable.getOwnerUUID() != null
                && ownable.getOwnerUUID().equals(player.getUUID());
    }

    private Pets() {
    }
}
