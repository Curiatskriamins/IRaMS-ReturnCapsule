package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapsuleProtection {
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(player)) {
            event.setCanceled(true);
        }
    }

    private static boolean isProtected(Player player) {
        return player.getVehicle() instanceof TransportCapsuleEntity;
    }

    private CapsuleProtection() {
    }
}
