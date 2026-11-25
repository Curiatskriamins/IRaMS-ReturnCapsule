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
    private static final java.util.Map<java.util.UUID, Integer> PURSUIT_HITS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<java.util.UUID, Float> PURSUIT_START_HEALTH = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<java.util.UUID, Long> PURSUIT_LAST_HIT = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int PURSUIT_WINDOW_TICKS = 100;

    private static final int PURSUIT_MIN_HITS = 5;

    private static final float PURSUIT_EFFECTIVE_DAMAGE = 10.0F;

    private static final int REGEN_BAN_TICKS = 1200;

    /**
     * Five fully charged hits that barely move the health bar (a target that regenerates faster than the blade cuts)
     * arm the pursuit; the follow up then also blocks all healing for a minute, so the damage actually sticks.
     */
    @SubscribeEvent
    public static void onPursuitLand(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player wielder)
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
        if (wielder.getAttackStrengthScale(0.5F) < 0.9F) {
            // Uncharged clicks do not count, so an auto clicker cannot farm the counter.
            return;
        }
        long now = target.level().getGameTime();
        java.util.UUID id = target.getUUID();
        Long last = PURSUIT_LAST_HIT.get(id);
        if (last == null || now - last > PURSUIT_WINDOW_TICKS) {
            PURSUIT_HITS.put(id, 0);
            PURSUIT_START_HEALTH.put(id, target.getHealth());
        }
        PURSUIT_LAST_HIT.put(id, now);
        int hits = PURSUIT_HITS.merge(id, 1, Integer::sum);
        if (hits < PURSUIT_MIN_HITS) {
            return;
        }
        float startHealth = PURSUIT_START_HEALTH.getOrDefault(id, target.getHealth());
        float effective = startHealth - target.getHealth();
        PURSUIT_HITS.remove(id);
        PURSUIT_START_HEALTH.remove(id);
        if (effective >= PURSUIT_EFFECTIVE_DAMAGE) {
            return;
        }
        target.invulnerableTime = 0;
        target.addEffect(new MobEffectInstance(ModEffects.PURSUIT.get(),
                ModEffects.PURSUIT_DURATION_TICKS, 0), wielder);
        applyMaxHealthPenalty(target, now);
    }

    private static final java.util.UUID PENALTY_ID =
            java.util.UUID.fromString("b7d1e2f3-4a56-4c78-9b90-1c2d3e4f5a60");

    /** 180 points of pursuit damage are taken out of the health bar instead of trying to ban regeneration. */
    private static final double PENALTY_AMOUNT = -180.0D;

    private static final java.util.Map<java.util.UUID, Long> PENALTY_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();

    private static void applyMaxHealthPenalty(LivingEntity target, long now) {
        if (target.getMaxHealth() < 150.0F) {
            return;
        }
        var attribute = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        if (attribute.getModifier(PENALTY_ID) == null) {
            attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    PENALTY_ID, "returncapsule:blade_penalty", PENALTY_AMOUNT,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        }
        PENALTY_UNTIL.put(target.getUUID(), now + REGEN_BAN_TICKS);
        target.setHealth(Math.min(target.getHealth(), target.getMaxHealth()));
    }

    @SubscribeEvent
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        java.util.UUID id = event.getEntity().getUUID();
        PURSUIT_HITS.remove(id);
        PURSUIT_START_HEALTH.remove(id);
        PURSUIT_LAST_HIT.remove(id);
        PENALTY_UNTIL.remove(id);
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        java.util.UUID id = event.getEntity().getUUID();
        PURSUIT_HITS.remove(id);
        PURSUIT_START_HEALTH.remove(id);
        PURSUIT_LAST_HIT.remove(id);
        PENALTY_UNTIL.remove(id);
    }

    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        Long until = PENALTY_UNTIL.get(entity.getUUID());
        if (until == null || entity.level().getGameTime() < until) {
            return;
        }
        PENALTY_UNTIL.remove(entity.getUUID());
        var attribute = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(PENALTY_ID);
        }
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
        if (!(wielder instanceof Player)) {
            // Only a player's blade strips max health. A base humanoid carrying the same blade only seals healing
            // through its own mechanic; it must never shrink anyone's pool.
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
            // Everything else loses max health until the cap. Past the cap nothing else happens: the excess is not
            // turned into true damage, the blade simply stops stripping that target.
            MaxHealthStrip.apply(target, percent,
                    (float) (Config.EQUIPMENT_BLADE_MAX_HEALTH_CAP.get() / 100.0D));
        } finally {
            applying = false;
        }
    }

    private StandardIssueBladeEvents() {
    }
}
