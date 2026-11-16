package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.util.DamageLock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies the humanoid's percentage damage to whatever it damages, by whatever means.
 * <p>
 * The earlier version only did this on melee hits, so a humanoid fighting with a rifle applied no percentage damage and
 * no heal lock at all — the wounded enemy simply regenerated back to full. Forge's damage event covers every source the
 * humanoid produces, including TaCZ rounds, which are entities of their own.
 * <p>
 * The share is taken straight out of health rather than sent back through damage. Routing it through damage would let
 * armour, resistance and SRP's adaptation reduce it, which defeats the point of a percentage; and the amount is also
 * recorded in {@link DamageLock} so the victim cannot heal it back.
 */
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
