package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanoidAlerts {
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof NeutralHumanoid shooter) {
            shooter.gunAim().onHit(victim);
        }
        if (!(victim instanceof Player) && !(victim instanceof NeutralHumanoid)) {
            return;
        }
        if (source instanceof LivingEntity attacker) {
            HumanoidSquad.report(victim.level(), attacker);
        }
    }

    private HumanoidAlerts() {
    }
}
