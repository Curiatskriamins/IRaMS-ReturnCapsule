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

    /** True while a real {@code hurt} call runs, so its own health write is not converted again. */
    private static boolean hurting;

    private record Remembered(LivingEntity attacker, long tick) {
    }

    public static boolean wearingFullSet(LivingEntity entity) {
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

    public static void setHurting(boolean value) {
        hurting = value;
    }

    public static boolean isHurting() {
        return hurting;
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
    }

    private static LivingEntity attackerOf(ServerPlayer player) {
        Remembered remembered = ATTACKERS.get(player.getUUID());
        if (remembered == null || remembered.attacker().isRemoved()
                || player.level().getGameTime() - remembered.tick() > MEMORY_TICKS) {
            return null;
        }
        return remembered.attacker();
    }

    /** Replays a direct health write through the set's mitigation ladder. */
    public static void convert(ServerPlayer player, float loss) {
        float maxHealth = player.getMaxHealth();
        if (loss < Config.EQUIPMENT_LOW_DAMAGE_IMMUNITY.get().floatValue()) {
            return;
        }
        UUID id = player.getUUID();
        int tick = player.tickCount;
        Integer last = LAST_HIT_TICK.get(id);
        if (last != null && tick - last < GATE_TICKS) {
            return;
        }
        LAST_HIT_TICK.put(id, tick);

        float reduced = loss * (1.0F - Config.EQUIPMENT_DAMAGE_REDUCTION.get().floatValue());
        reduced = Math.min(reduced, maxHealth * Config.EQUIPMENT_HIT_CAP_FRACTION.get().floatValue());

        long gameTime = player.level().getGameTime();
        long second = gameTime / 20L;
        long known = BUDGET_SECOND.getOrDefault(id, Long.MIN_VALUE);
        float spent = known == second ? BUDGET_TAKEN.getOrDefault(id, 0.0F) : 0.0F;
        BUDGET_SECOND.put(id, second);
        float allowance = maxHealth * Config.EQUIPMENT_SECOND_CAP_FRACTION.get().floatValue() - spent;
        if (allowance <= 0.0F) {
            return;
        }
        reduced = Math.min(reduced, allowance);
        BUDGET_TAKEN.put(id, spent + reduced);

        if (reduced <= 0.0F) {
            return;
        }
        if (player.getHealth() - reduced <= 0.0F && gameTime >= LAST_STAND_READY.getOrDefault(id, Long.MIN_VALUE)) {
            LAST_STAND_READY.put(id, gameTime + LAST_STAND_COOLDOWN_TICKS);
            hurting = true;
            try {
                player.setHealth(1.0F);
            } finally {
                hurting = false;
            }
            player.invulnerableTime = LAST_STAND_GRACE_TICKS;
            return;
        }
        player.hurt(player.damageSources().mobAttack(attackerOf(player)), reduced);
    }

    /**
     * A write straight to zero is an execution, not a hit. It has to reach the vanilla death path, so it skips the
     * mitigation ladder entirely - otherwise the ceiling would leave a zero health player who never died.
     */
    public static void execute(ServerPlayer player) {
        player.hurt(player.damageSources().mobAttack(attackerOf(player)), player.getMaxHealth() * 8.0F);
    }

    private PlayerEquipment() {
    }
}
