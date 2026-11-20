package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.item.HumanoidArmorItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player side counterpart of the base humanoid's damage conversion.
 *
 * <p>The parasite mod does not damage through {@code hurt}: it writes health directly, which skips armour, effects,
 * invulnerability frames and the death path entirely. The set answers the same way the humanoid does - cancel the write
 * and replay it through the vanilla pipeline - and then passes it through the same ladder of filters the humanoid uses,
 * scaled to twenty health:</p>
 *
 * <ol>
 * <li>low damage immunity, a share of max health;</li>
 * <li>a four tick gate, matching the humanoid's own gate (vanilla invulnerability still sits on top);</li>
 * <li>flat reduction, fifty percent by default;</li>
 * <li>a per hit ceiling of a fifth of max health;</li>
 * <li>a per second ceiling of a quarter of max health, the swarm valve;</li>
 * <li>last stand, once per cooldown, leaving the player at one health for a short grace.</li>
 * </ol>
 *
 * <p>There is deliberately no shield pool: the mitigation is expressed as damage reduction, so nothing new has to be
 * displayed, synchronised or saved.</p>
 */
public final class PlayerEquipment {

    private static final Map<UUID, Remembered> ATTACKERS = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> LAST_HIT_TICK = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> BUDGET_SECOND = new ConcurrentHashMap<>();

    private static final Map<UUID, Float> BUDGET_TAKEN = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> LAST_STAND_READY = new ConcurrentHashMap<>();

    /** Last attacker is only trusted this long, in ticks. */
    private static final int MEMORY_TICKS = 60;

    /** Same gate the humanoid uses. */
    private static final int GATE_TICKS = 4;

    private static final long LAST_STAND_COOLDOWN_TICKS = 1800L;

    private static final int LAST_STAND_GRACE_TICKS = 60;

    private record Remembered(LivingEntity attacker, long tick) {
    }

    /**
     * Tick on which a real {@code hurt} call last ran for this player.
     *
     * <p>The vanilla damage pipeline writes health itself; that write must pass through untouched, while a write from
     * a mob that ticks health directly must be converted. Invulnerability time cannot tell them apart (a direct write
     * usually lands while the player is still invulnerable), so the wrapper around {@code hurt} stamps the tick
     * instead. Storing a tick rather than a boolean means a stamp left behind by an exception expires by itself at the
     * next tick instead of silently disabling the set forever.</p>
     */
    private static final Map<UUID, Integer> HURT_TICK = new ConcurrentHashMap<>();

    public static void markHurt(ServerPlayer player) {
        HURT_TICK.put(player.getUUID(), player.tickCount);
    }

    public static boolean insideHurt(ServerPlayer player) {
        Integer tick = HURT_TICK.get(player.getUUID());
        return tick != null && tick == player.tickCount;
    }

    public static boolean wearingFullSet(LivingEntity entity) {
        // A player still under construction has no inventory yet: LivingEntity's own constructor sets health before
        // Player assigns it, and the set health hook runs then. Reading equipment at that point throws.
        if (entity instanceof net.minecraft.world.entity.player.Player player && player.getInventory() == null) {
            return false;
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof HumanoidArmorItem)) {
                return false;
            }
        }
        return true;
    }

    /** The blade counts in either hand. */
    public static boolean holdingBlade(LivingEntity entity) {
        return entity.getMainHandItem().is(com.curiats.returncapsule.registry.ModItems.STANDARD_ISSUE_SWORD.get())
                || entity.getOffhandItem().is(com.curiats.returncapsule.registry.ModItems.STANDARD_ISSUE_SWORD.get());
    }

    public static void remember(ServerPlayer player, DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
            ATTACKERS.put(player.getUUID(), new Remembered(attacker, player.level().getGameTime()));
        }
    }

    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        ATTACKERS.remove(id);
        LAST_HIT_TICK.remove(id);
        BUDGET_SECOND.remove(id);
        BUDGET_TAKEN.remove(id);
        LAST_STAND_READY.remove(id);
        HURT_TICK.remove(id);
    }

    private static LivingEntity attackerOf(ServerPlayer player) {
        Remembered remembered = ATTACKERS.get(player.getUUID());
        if (remembered == null || remembered.attacker().isRemoved()
                || player.level().getGameTime() - remembered.tick() > MEMORY_TICKS) {
            return null;
        }
        return remembered.attacker();
    }

    /**
     * Replays a direct health write as one physical hit. Mitigation is not applied here: the set's ladder lives in
     * PlayerEquipmentDamage and runs for every hit, converted or not.
     */
    public static void convert(ServerPlayer player, float loss) {
        if (loss <= 0.0F) {
            return;
        }
        // Mitigate before handing the hit over: a burst of direct writes drains the budget here, and nothing
        // that takes the hit over afterwards can undo it.
        float allowed = com.curiats.returncapsule.event.PlayerEquipmentDamage.mitigate(player, loss);
        if (allowed <= 0.0F) {
            return;
        }
        com.curiats.returncapsule.event.PlayerEquipmentDamage.markConverted(player);
        player.hurt(player.damageSources().mobAttack(attackerOf(player)), allowed);
    }
    private PlayerEquipment() {
    }
}
