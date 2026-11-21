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
        if (com.curiats.returncapsule.util.Pets.isPet(victim)) {
            // Player pets are off limits for the percentage strip and the seal.
            return;
        }
        if (victim.getHealth() <= 0.0F) {
            return;
        }
        // The seal applies to every humanoid hit, melee or gun. A base humanoid never removes health or max health:
        // the target simply cannot heal back above maxHealth - sealed until this humanoid dies.
        DamageLock.lock(victim, victim.level().getGameTime(), humanoid.getUUID());
    }
}
