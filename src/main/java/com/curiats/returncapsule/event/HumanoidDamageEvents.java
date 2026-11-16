package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.util.DamageLock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID)
public final class HumanoidDamageEvents {
    private HumanoidDamageEvents() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof NeutralHumanoid humanoid)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof NeutralHumanoid || victim instanceof Player || victim.level().isClientSide) {
            return;
        }
        if (victim.getHealth() <= 0.0F) {
            return;
        }
        if (victim.getHealth() <= 0.0F) {
            DamageLock.forget(victim);
            return;
        }
        float share = victim.getMaxHealth() * DamageLock.SHARE;
        DamageLock.lock(victim, victim.level().getGameTime());
        victim.setHealth(Math.max(0.0F, victim.getHealth() - share));
    }
}
