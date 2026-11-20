package com.curiats.returncapsule.event;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.PlayerEquipment;
import com.curiats.returncapsule.util.TrueDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The standard issue set's mitigation ladder.
 *
 * <p>It is called from two places with the same rules:</p>
 * <ul>
 * <li>the set health hook, <em>before</em> the converted hit is handed to {@code hurt}, so a burst of direct writes can
 *     never exceed the budget no matter what takes the hit over afterwards;</li>
 * <li>the hurt event, before armour, so an ordinary hit is reduced exactly like a converted write.</li>
 * </ul>
 *
 * <p>Ladder: low damage immunity, a four tick gate, flat reduction, a per hit ceiling, a per second ceiling, then last
 * stand. Maps are keyed by player and shared by both callers, so a converted write and a normal attack draw from the
 * same budget.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerEquipmentDamage {

    private static final Map<UUID, Integer> LAST_HIT_TICK = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> BUDGET_SECOND = new ConcurrentHashMap<>();

    private static final Map<UUID, Float> BUDGET_TAKEN = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> LAST_STAND_READY = new ConcurrentHashMap<>();

    /** Tick on which a hit was already mitigated by the conversion path, so the damage event does not do it twice. */
    private static final Map<UUID, Integer> CONVERTED_TICK = new ConcurrentHashMap<>();

    private static final int GATE_TICKS = 4;

    private static final long LAST_STAND_COOLDOWN_TICKS = 1800L;

    private static final int LAST_STAND_GRACE_TICKS = 60;

    private static final java.util.concurrent.atomic.AtomicLong HITS = new java.util.concurrent.atomic.AtomicLong();

    public static void markConverted(ServerPlayer player) {
        CONVERTED_TICK.put(player.getUUID(), player.tickCount);
    }

    private static boolean wasConverted(ServerPlayer player) {
        Integer tick = CONVERTED_TICK.get(player.getUUID());
        return tick != null && tick == player.tickCount;
    }

    /**
     * Applies the whole ladder to one hit.
     *
     * @return the damage that may still be dealt, or a negative value when the hit is fully absorbed
     */
    public static float mitigate(ServerPlayer player, float amount) {
        if (amount <= 0.0F) {
            return -1.0F;
        }
        if (amount < Config.EQUIPMENT_LOW_DAMAGE_IMMUNITY.get().floatValue()) {
            return -1.0F;
        }
        UUID id = player.getUUID();
        int tick = player.tickCount;
        Integer last = LAST_HIT_TICK.get(id);
        if (last != null && tick - last < GATE_TICKS) {
            return -1.0F;
        }
        LAST_HIT_TICK.put(id, tick);

        float maxHealth = player.getMaxHealth();
        float reduced = amount * (1.0F - Config.EQUIPMENT_DAMAGE_REDUCTION.get().floatValue());
        reduced = Math.min(reduced, maxHealth * Config.EQUIPMENT_HIT_CAP_FRACTION.get().floatValue());

        long gameTime = player.level().getGameTime();
        long second = gameTime / 20L;
        long known = BUDGET_SECOND.getOrDefault(id, Long.MIN_VALUE);
        float spent = known == second ? BUDGET_TAKEN.getOrDefault(id, 0.0F) : 0.0F;
        BUDGET_SECOND.put(id, second);
        float allowance = maxHealth * Config.EQUIPMENT_SECOND_CAP_FRACTION.get().floatValue() - spent;
        if (allowance <= 0.0F) {
            return -1.0F;
        }
        reduced = Math.min(reduced, allowance);
        BUDGET_TAKEN.put(id, spent + reduced);

        if (player.getHealth() - reduced <= 0.0F
                && gameTime >= LAST_STAND_READY.getOrDefault(id, Long.MIN_VALUE)) {
            LAST_STAND_READY.put(id, gameTime + LAST_STAND_COOLDOWN_TICKS);
            player.invulnerableTime = LAST_STAND_GRACE_TICKS;
            // Expressed as damage rather than a direct health write: a write here would be seen by our own set health
            // hook as another direct write and converted again.
            return Math.max(0.0F, player.getHealth() - 1.0F);
        }
        return reduced;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.EQUIPMENT_SET_ENABLED.get()) {
            return;
        }
        if (!PlayerEquipment.wearingFullSet(player) || wasConverted(player)) {
            return;
        }
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            // /kill and the void stay lethal regardless of the set.
            return;
        }
        if (event.getSource().is(TrueDamage.CAPSULE_IMPACT) || event.getSource().is(TrueDamage.BOOM_IMPACT)
                || event.getSource().is(TrueDamage.STANDARD_ISSUE_BLADE)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        float reduced = mitigate(player, amount);
        if (HITS.incrementAndGet() % 100 == 1) {
            ReturnCapsuleMod.LOGGER.info("[equipment] hit {} -> {} (source={}, health={}, max={})",
                    amount, reduced, event.getSource().getMsgId(), player.getHealth(), player.getMaxHealth());
        }
        if (reduced < 0.0F) {
            event.setCanceled(true);
            return;
        }
        event.setAmount(reduced);
    }

    /**
     * Absolute backstop on the damage that finally lands.
     *
     * <p>The ladder above already caps a hit before armour, which is the balance-correct place. This second pass runs
     * after everything else and only enforces a hard ceiling on the landed number, so a path that somehow skipped the
     * ladder (or re-ran it) can never delete a player in one hit.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.EQUIPMENT_SET_ENABLED.get()) {
            return;
        }
        if (!PlayerEquipment.wearingFullSet(player)) {
            return;
        }
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                || event.getSource().is(TrueDamage.CAPSULE_IMPACT)
                || event.getSource().is(TrueDamage.BOOM_IMPACT)
                || event.getSource().is(TrueDamage.STANDARD_ISSUE_BLADE)) {
            return;
        }
        float ceiling = player.getMaxHealth() * 0.5F;
        if (event.getAmount() > ceiling) {
            event.setAmount(ceiling);
        }
    }
    /** Clears a stale converted marker; called from the same tick as the write. */
    public static void clearConverted(ServerPlayer player) {
        CONVERTED_TICK.remove(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        LAST_HIT_TICK.remove(id);
        BUDGET_SECOND.remove(id);
        BUDGET_TAKEN.remove(id);
        LAST_STAND_READY.remove(id);
        CONVERTED_TICK.remove(id);
    }

    private PlayerEquipmentDamage() {
    }
}
