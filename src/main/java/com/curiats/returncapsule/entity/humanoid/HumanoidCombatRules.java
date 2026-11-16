package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

public final class HumanoidCombatRules {

    public static final double SCAN_RADIUS = 500.0D;

    public static final double SCAN_VERTICAL_RADIUS = 64.0D;

    public static final int SCAN_INTERVAL = 40;

    public static final double MELEE_ENGAGEMENT = 24.0D;

    public static final double ALLY_RESCUE_RADIUS = 48.0D;

    public static final double FOLLOW_RADIUS = 32.0D;

    public static final double LENIENCE = 1.5D;

    public static final double SHIELD_DISTANCE = 4.0D;

    public static final double MELEE_REACH = 3.0D;

    public static final double MELEE_SWITCH_DISTANCE = 8.0D;

    private static final double RADIUS_PER_SCORE = 0.362D;
    private static final double RADIUS_BASE = 10.5D;
    private static final double MIN_ENGAGEMENT = 30.0D;
    private static final double MAX_ENGAGEMENT = 500.0D;

    private static final double HOSTILITY_BONUS = 1000.0D;

    public static boolean isHostileEnemy(LivingEntity target) {
        return target instanceof Enemy && !(target instanceof NeutralMob);
    }

    public static double score(LivingEntity target) {
        return target.getMaxHealth()
                + attribute(target, Attributes.ATTACK_DAMAGE) * 10.0D
                + attribute(target, Attributes.ARMOR) * 2.0D;
    }

    public static double threatRadius(LivingEntity target) {
        return Mth.clamp(RADIUS_BASE + RADIUS_PER_SCORE * score(target), MIN_ENGAGEMENT, MAX_ENGAGEMENT);
    }

    public static double engagementRadius(LivingEntity target) {
        double threat = threatRadius(target);
        return threatensAllies(target) ? Math.max(ALLY_RESCUE_RADIUS, threat) : threat;
    }

    public static double priority(LivingEntity target) {
        return priority(null, target);
    }

    public static double priority(LivingEntity user, LivingEntity target) {
        double value = score(target);
        if (threatensAllies(target)) {
            value += HOSTILITY_BONUS;
        }

        double value2 = value + playerProximityBonus(target);

        if (user == null) {
            return value2;
        }
        double distance = Math.sqrt(user.distanceToSqr(target));
        return value2 - Math.min(distance, NEAREST_CLAMP) * NEAREST_WEIGHT;
    }

    private static double playerProximityBonus(LivingEntity target) {
        Player nearest = target.level().getNearestPlayer(target, GUARD_RADIUS);
        if (nearest == null) {
            return 0.0D;
        }
        double distance = Math.sqrt(target.distanceToSqr(nearest));
        return PROXIMITY_BONUS * Math.max(0.0D, 1.0D - distance / GUARD_RADIUS);
    }

    private static final double GUARD_RADIUS = 24.0D;

    private static final double PROXIMITY_BONUS = 600.0D;

    private static final double NEAREST_WEIGHT = 60.0D;
    private static final double NEAREST_CLAMP = 48.0D;

    private static final double NEAREST_RADIUS = 32.0D;

    public static boolean threatensAllies(LivingEntity target) {
        if (!(target instanceof Mob mob)) {
            return false;
        }
        LivingEntity victim = mob.getTarget();
        return victim instanceof Player || victim instanceof NeutralHumanoid;
    }

    private static double attribute(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    private HumanoidCombatRules() {
    }
}
