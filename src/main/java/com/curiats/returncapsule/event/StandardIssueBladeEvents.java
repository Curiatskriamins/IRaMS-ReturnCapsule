package com.curiats.returncapsule.event;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.compat.PlayerEquipment;
import com.curiats.returncapsule.registry.ModEffects;
import com.curiats.returncapsule.util.TrueDamage;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
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

    private static final java.util.Map<java.util.UUID, Long> GUARD_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Once per incoming hit there is a chance to shrug the whole thing off and stay untouchable for a short window.
     * Runs before the mitigation ladder, so a cancelled hit never even draws from the budget.
     */
    /**
     * Arms the pursuit effect. The damage itself is dealt by the effect's tick, which clears invulnerability frames
     * before each instance - the same shape as the mimic armory's red mark - so the follow up is felt even while the
     * target is still inside the twenty tick window granted by the blade hit that armed it.
     */
    @SubscribeEvent
    public static void onPursuitLand(LivingDamageEvent event) {
        if (!Config.EQUIPMENT_PURSUIT_ENABLED.get()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity wielder)
                || !PlayerEquipment.holdingBlade(wielder)) {
            return;
        }
        if (event.getSource().is(TrueDamage.STANDARD_ISSUE_BLADE)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || target.isDeadOrDying()) {
            return;
        }
        if (target.getMaxHealth() <= Config.EQUIPMENT_PURSUIT_MIN_HEALTH.get()) {
            return;
        }
        // Clear the frames the sword hit just granted, so the very first tick of the effect is not eaten and the target
        // never gets a free window right after being hit.
        target.invulnerableTime = 0;
        target.addEffect(new MobEffectInstance(ModEffects.PURSUIT.get(),
                Config.EQUIPMENT_PURSUIT_WINDOW_TICKS.get(), 0), wielder);
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onGuard(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!Config.EQUIPMENT_GUARD_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!PlayerEquipment.wearingFullSet(player) && !PlayerEquipment.holdingBlade(player)) {
            return;
        }
        long now = player.level().getGameTime();
        Long until = GUARD_UNTIL.get(player.getUUID());
        if (until != null && now < until) {
            event.setCanceled(true);
            return;
        }
        if (player.getRandom().nextFloat() < Config.EQUIPMENT_GUARD_CHANCE.get().floatValue()) {
            GUARD_UNTIL.put(player.getUUID(), now + Config.EQUIPMENT_GUARD_TICKS.get());
            event.setCanceled(true);
        }
    }
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
                // Direct health write on purpose: this is a payment, not a hit, so it must not run through armour or the
                // invulnerability window. The conversion hook leaves it alone while the player is invulnerable, so give
                // the payment that window for the duration of the write.
                int previousInvulnerability = player.invulnerableTime;
                player.invulnerableTime = 20;
                try {
                    player.setHealth(player.getHealth() - cost);
                } finally {
                    player.invulnerableTime = previousInvulnerability;
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
