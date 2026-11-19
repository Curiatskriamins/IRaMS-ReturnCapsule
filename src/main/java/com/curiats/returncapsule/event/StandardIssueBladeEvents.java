package com.curiats.returncapsule.event;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.registry.ModItems;
import com.curiats.returncapsule.util.MaxHealthStrip;
import com.curiats.returncapsule.util.Pets;
import com.curiats.returncapsule.util.TrueDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Percentage health strip of the standard issue blade.
 *
 * <p>Every hit that lands from the blade takes a flat percentage of the target's max health <em>in addition</em> to the
 * sword's normal, heavily enchanted damage:</p>
 * <ul>
 * <li>players take it as percentage true damage, so death messages, totems and death handling stay vanilla;</li>
 * <li>everything else takes it as a straight health deduction that ignores armour, effects and invulnerability, with the
 *     lethal case routed back through {@code hurt} so the normal death path still runs.</li>
 * </ul>
 *
 * <p>The strip is applied once per swing: the re-entrant {@code hurt} for the lethal case would otherwise fire this
 * event again for the same attacker.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StandardIssueBladeEvents {

    private static boolean applying;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (applying || !Config.EQUIPMENT_BLADE_ENABLED.get()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || target.isDeadOrDying()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity wielder)) {
            return;
        }
        if (!wielder.getMainHandItem().is(ModItems.STANDARD_ISSUE_SWORD.get())) {
            return;
        }
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (Pets.isPet(target)) {
            // Pets never lose max health. A sneaking owner swings to trade health into the pet instead: the swing deals
            // no damage, the player pays a share of their max health and the pet gets exactly that back. The payment is
            // limited so the player never drops below a small floor of their own pool.
            if (wielder instanceof Player player && player.isShiftKeyDown() && Pets.isOwnedBy(target, player)) {
                event.setCanceled(true);
                float share = player.getMaxHealth()
                        * Config.EQUIPMENT_SUPPORT_HEAL_PERCENT.get().floatValue() / 100.0F;
                float floor = player.getMaxHealth()
                        * Config.EQUIPMENT_BLADE_PLAYER_FLOOR.get().floatValue() / 100.0F;
                float cost = Math.min(share, Math.max(0.0F, player.getHealth() - floor));
                if (cost <= 0.0F) {
                    return;
                }
                com.curiats.returncapsule.compat.PlayerEquipment.setHurting(true);
                try {
                    player.setHealth(player.getHealth() - cost);
                } finally {
                    com.curiats.returncapsule.compat.PlayerEquipment.setHurting(false);
                }
                target.heal(cost);
            }
            return;
        }
        float percent = (float) (Config.EQUIPMENT_BLADE_PERCENT.get() / 100.0D);
        if (percent <= 0.0F) {
            return;
        }
        float strip = target.getMaxHealth() * percent;
        if (strip <= 0.0F) {
            return;
        }
        applying = true;
        try {
            if (target instanceof Player) {
                target.hurt(TrueDamage.blade(level, wielder, wielder), strip);
                return;
            }
            // Everything else loses max health until the cap, then converts to true damage.
            MaxHealthStrip.Result result = MaxHealthStrip.apply(target, percent,
                    (float) (Config.EQUIPMENT_BLADE_MAX_HEALTH_CAP.get() / 100.0D));
            if (result.excess() > 0.0F) {
                target.hurt(TrueDamage.blade(level, wielder, wielder), result.excess());
            }
        } finally {
            applying = false;
        }
    }

    private StandardIssueBladeEvents() {
    }
}
