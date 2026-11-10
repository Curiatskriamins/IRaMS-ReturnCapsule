package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.util.HiddenPlayers;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapsuleProtection {
    @SubscribeEvent
    public static void onPearlImpact(ProjectileImpactEvent event) {
        Level level = event.getProjectile().level();
        if (level.isClientSide || !(event.getProjectile() instanceof ThrownEnderpearl pearl)) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)) {
            return;
        }
        if (PearlBoarding.tryBoard(pearl, hit)) {
            event.setCanceled(true);
        }
    }

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

    public static boolean isProtected(Player player) {
        return HiddenPlayers.isHidden(player) || player.getVehicle() instanceof TransportCapsuleEntity;
    }

    private CapsuleProtection() {
    }
}
