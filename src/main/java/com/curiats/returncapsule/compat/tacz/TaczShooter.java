package com.curiats.returncapsule.compat.tacz;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public final class TaczShooter {
    private static final String OPERATOR_CLASS = "com.tacz.guns.api.entity.IGunOperator";
    private static final String AMMO_TAG = "GunCurrentAmmoCount";
    private static final String BARREL_TAG = "HasBulletInBarrel";
    private static final int REFILL_MAGAZINE = 30;

    private static volatile boolean resolved;
    private static volatile boolean broken;
    private static final java.util.Set<String> reportedOutcomes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<Integer> shotsLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static Method fromLivingEntity;
    private static Method draw;
    private static Method aim;
    private static Method shoot;
    private static Method synDrawCoolDown;
    private static Method bolt;
    private static Method synShootCoolDown;
    private static Method bolting;

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

    /** @return true when a round actually left the barrel */
    public static boolean fire(NeutralHumanoid mob, LivingEntity target) {
        if (!resolve()) {
            return false;
        }
        ItemStack gun = mob.getMainHandItem();
        if (!TaczRifles.isServiceGun(gun)) {
            reportOutcome("NOT_A_SERVICE_GUN", target);
            return false;
        }
        try {
            Object operator = operator(mob);
            if (!mob.isGunDrawn()) {
                draw.invoke(operator, (Supplier<ItemStack>) () -> mob.getMainHandItem());
                mob.setGunDrawn(true);
                return false;
            }

            if (!ready(operator)) {
                return false;
            }
            TaczRifles.prepareForMob(gun);

            mob.setSprinting(false);
            Object result = shoot.invoke(operator,
                    (Supplier<Float>) mob::getXRot, (Supplier<Float>) mob::getYHeadRot);
            String outcome = result instanceof Enum<?> value ? value.name() : "UNKNOWN";
            reportOutcome(outcome, target);
            if (!"SUCCESS".equals(outcome)) {
                recover(outcome, mob, gun);
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce(exception);
            return false;
        }
    }

    private static Method bolting() {
        return bolting;
    }

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

    private static void recover(String outcome, NeutralHumanoid mob, ItemStack gun) {
        switch (outcome) {
            case "NEED_BOLT" -> {

                if (bolt != null) {
                    try {
                        bolt.invoke(operator(mob));
                    } catch (ReflectiveOperationException | RuntimeException ignored) {

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

            }
        }
    }

    private static void reportOutcome(String outcome, LivingEntity target) {
        if ("SUCCESS".equals(outcome)) {
            reportedOutcomes.clear();
            return;
        }
        if (!reportedOutcomes.add(outcome)) {
            return;
        }
        ReturnCapsuleMod.LOGGER.info("[gun] TaCZ refused a shot: {} (target {})", outcome, target.getName().getString());
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
                bolting = operator.getMethod("getSynIsBolting");
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
