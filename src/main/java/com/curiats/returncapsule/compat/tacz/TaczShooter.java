package com.curiats.returncapsule.compat.tacz;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Firing bridge between a humanoid and TaCZ.
 * <p>
 * TaCZ is not a compile time dependency, so everything is reflective. The important detail is that TaCZ does not
 * need a mob-specific API: its mixin makes every {@code LivingEntity} implement {@code IGunOperator}, so the same
 * draw/aim/shoot calls the client uses for a player also work for a mob.
 * <p>
 * Two sequencing rules come from TaCZ's implementation and are handled here:
 * <ul>
 *   <li>{@code shoot} returns {@code NOT_DRAW} until {@code draw} has been called once for the current gun, so the
 *       first attack after equipping only draws the weapon;</li>
 *   <li>{@code draw} starts a put-away cooldown, so a shot immediately after it reports {@code IS_DRAWING}.</li>
 * </ul>
 */
public final class TaczShooter {
    private static final String OPERATOR_CLASS = "com.tacz.guns.api.entity.IGunOperator";
    private static final String AMMO_TAG = "GunCurrentAmmoCount";
    private static final String BARREL_TAG = "HasBulletInBarrel";
    private static final int REFILL_MAGAZINE = 30;

    private static volatile boolean resolved;
    private static volatile boolean broken;
    private static final java.util.Set<String> reportedOutcomes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static Method fromLivingEntity;
    private static Method draw;
    private static Method aim;
    private static Method shoot;
    private static Method synDrawCoolDown;
    private static Method bolt;
    private static Method synShootCoolDown;

    /**
     * Keeps the mob's aim state in sync with its combat state. Cheap to call every tick: TaCZ's aim call is a
     * data-field check, and it is also what drives the third-person gun pose on the client.
     */
    public static void tick(NeutralHumanoid mob) {
        if (!resolve() || !mob.holdsGun()) {
            return;
        }
        try {
            aim.invoke(operator(mob), mob.getTarget() != null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce(exception);
        }
    }

    /** Clears the aim pose when the mob stops carrying a rifle. */
    public static void aimOff(NeutralHumanoid mob) {
        if (!resolve()) {
            return;
        }
        try {
            aim.invoke(operator(mob), false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce(exception);
        }
    }

    /** Fires one round at the current target. Called from the combat goal on the server. */
    public static void fire(NeutralHumanoid mob, LivingEntity target) {
        if (!resolve()) {
            return;
        }
        ItemStack gun = mob.getMainHandItem();
        if (!TaczRifles.isServiceGun(gun)) {
            reportOutcome("NOT_A_SERVICE_GUN", target);
            return;
        }
        try {
            Object operator = operator(mob);
            if (!mob.isGunDrawn()) {
                draw.invoke(operator, (Supplier<ItemStack>) () -> mob.getMainHandItem());
                mob.setGunDrawn(true);
                return;
            }
            // Let TaCZ's own synced cooldowns decide when the weapon is ready. Without this the AI pulls the trigger
            // through the whole draw animation and every shot is refused with IS_DRAWING.
            if (!ready(operator)) {
                return;
            }
            TaczRifles.prepareForMob(gun);
            // TaCZ rejects a shot while the shooter is sprinting; a guard closing in must not be locked out.
            mob.setSprinting(false);
            Object result = shoot.invoke(operator,
                    (Supplier<Float>) mob::getXRot, (Supplier<Float>) mob::getYHeadRot);
            String outcome = result instanceof Enum<?> value ? value.name() : "UNKNOWN";
            reportOutcome(outcome, target);
            if (!"SUCCESS".equals(outcome)) {
                recover(outcome, mob, gun);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce(exception);
        }
    }

    /** True when both the draw and the shot cooldown TaCZ reports are finished. */
    private static boolean ready(Object operator) throws ReflectiveOperationException {
        if (synDrawCoolDown == null || synShootCoolDown == null) {
            return true;
        }
        return toLong(synDrawCoolDown.invoke(operator)) <= 0L
                && toLong(synShootCoolDown.invoke(operator)) <= 0L;
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * Recovers from the two outcomes that are our fault rather than TaCZ's: a lost draw state (for example after a
     * weapon swap) and an empty magazine. Everything else is TaCZ saying "not now" and is left to the next attack tick.
     */
    private static void recover(String outcome, NeutralHumanoid mob, ItemStack gun) {
        switch (outcome) {
            case "NEED_BOLT" -> {
                // Bolt-action rifles will not fire again until the bolt is cycled, and a mob has no player input to do
                // it. TaCZ exposes the action, so the humanoid cycles its own bolt.
                if (bolt != null) {
                    try {
                        bolt.invoke(operator(mob));
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // Nothing better to do; the next attempt will report the same outcome.
                    }
                }
            }
            case "NOT_DRAW" -> mob.setGunDrawn(false);
            case "NO_AMMO" -> {
                gun.getOrCreateTag().putInt(AMMO_TAG, REFILL_MAGAZINE);
                gun.getOrCreateTag().putBoolean(BARREL_TAG, true);
                TaczRifles.prepareForMob(gun);
            }
            default -> {
                // COOL_DOWN / IS_DRAWING / IS_RELOADING / IS_BOLTING: nothing to fix, just wait a tick.
            }
        }
    }

    /**
     * TaCZ reports why a shot did not happen, and each reason has a completely different fix ({@code NOT_DRAW} means
     * the draw call did not take, {@code NO_AMMO} means the script-level ammo check refused, {@code COOL_DOWN} is just
     * cadence). Logging each distinct outcome once turns "the gun never fires" into a concrete answer in the log.
     */
    private static void reportOutcome(String outcome, LivingEntity target) {
        if ("SUCCESS".equals(outcome)) {
            reportedOutcomes.clear();
            return;
        }
        if (!reportedOutcomes.add(outcome)) {
            return;
        }
        ReturnCapsuleMod.LOGGER.info("[humanoid] TaCZ refused a shot: {} (target {})", outcome, target.getName().getString());
    }

    private static Object operator(NeutralHumanoid mob) throws ReflectiveOperationException {
        return fromLivingEntity.invoke(null, mob);
    }

    private static void warnOnce(Exception exception) {
        if (broken) {
            return;
        }
        broken = true;
        ReturnCapsuleMod.LOGGER.warn("TaCZ firing bridge hit an error and will keep logging only once",
                exception);
    }

    private static boolean resolve() {
        if (resolved) {
            return fromLivingEntity != null;
        }
        synchronized (TaczShooter.class) {
            if (resolved) {
                return fromLivingEntity != null;
            }
            resolved = true;
            try {
                Class<?> operator = Class.forName(OPERATOR_CLASS);
                fromLivingEntity = operator.getMethod("fromLivingEntity", LivingEntity.class);
                draw = operator.getMethod("draw", Supplier.class);
                aim = operator.getMethod("aim", boolean.class);
                shoot = operator.getMethod("shoot", Supplier.class, Supplier.class);
                synDrawCoolDown = operator.getMethod("getSynDrawCoolDown");
                bolt = operator.getMethod("bolt");
                synShootCoolDown = operator.getMethod("getSynShootCoolDown");
                ReturnCapsuleMod.LOGGER.debug("[humanoid] TaCZ firing bridge ready");
            } catch (ReflectiveOperationException | RuntimeException exception) {
                fromLivingEntity = null;
                ReturnCapsuleMod.LOGGER.debug("TaCZ is not available for humanoid gunfire: {}",
                        exception.toString());
            }
            return fromLivingEntity != null;
        }
    }

    private TaczShooter() {
    }
}
