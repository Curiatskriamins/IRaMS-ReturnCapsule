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

/**
 * Decides whether a mob is a target, and how far away a humanoid is willing to deal with it.
 * <p>
 * There are exactly two reasons to fight, and they use different ranges:
 * <ol>
 *   <li><b>It is hostile.</b> A mob that is {@link Enemy} and <em>not</em> {@link NeutralMob} is something that will
 *       attack anyway, so it is worth clearing. How far away depends on how dangerous it is — a zombie 30 blocks, a
 *       Warden 300 (see below).</li>
 *   <li><b>It attacked one of ours.</b> Anyone who hits a player or a humanoid is clearing-worthy regardless of what
 *       kind of mob it is, but only within a distance that feels reasonable from a player's point of view — you do not
 *       want the whole map converging because a wolf bit someone 200 blocks away.</li>
 * </ol>
 * Neutral mobs are therefore never picked up on their own: an Enderman is dangerous but minding its own business, and
 * a zombified piglin or an iron golem is not a target at all. Threat value alone is never a reason to attack.
 * <p>
 * The threat curve is a straight line calibrated against two reference points from the design sheet; both land
 * exactly on the requested numbers:
 * <table>
 *   <tr><th>target</th><th>score</th><th>range as a hostile</th></tr>
 *   <tr><td>Zombie (20 HP, 3 damage, 2 armour)</td><td>54</td><td>30 blocks</td></tr>
 *   <tr><td>Warden (500 HP, 30 damage)</td><td>800</td><td>300 blocks</td></tr>
 * </table>
 */
public final class HumanoidCombatRules {
    /** How far a humanoid looks for work. Detection is shared through {@link HumanoidSquad}. */
    public static final double SCAN_RADIUS = 500.0D;
    /** Vertical half-height of the scan box; see the note in {@code HumanoidTargetGoal#scanArea}. */
    public static final double SCAN_VERTICAL_RADIUS = 64.0D;
    /** Ticks between two scans of one humanoid; scans are staggered by entity id so they never all run together. */
    public static final int SCAN_INTERVAL = 40;
    /** A blade-only humanoid never picks a fight beyond this, however valuable the target is. */
    public static final double MELEE_ENGAGEMENT = 24.0D;
    /**
     * A humanoid answering a call for help only does so within this distance, whatever the attacker is worth. This is
     * the "appropriate range" from the player's side: close enough that help feels immediate, far enough that the whole
     * squad does not cross the map over one stray hit.
     */
    public static final double ALLY_RESCUE_RADIUS = 48.0D;
    /** How far a humanoid will travel to stay with its player when it has nothing to fight. */
    public static final double FOLLOW_RADIUS = 32.0D;
    /** Once engaged, a target may drift this much past its engagement range before being dropped. */
    public static final double LENIENCE = 1.5D;
    /** A shield is raised when the threat is inside this range. */
    public static final double SHIELD_DISTANCE = 4.0D;
    /**
     * Melee reach in blocks. Vanilla mobs use a reach derived from their hitbox, which works out to roughly 1.4 blocks
     * for a player-sized body — far shorter than the ~3 blocks a player swings at, and the reason attacks felt like they
     * never connected.
     */
    public static final double MELEE_REACH = 3.0D;
    /**
     * Distance at which a humanoid decides to close in and use its blade instead of a gun. This is not the swing reach:
     * the blade still connects at {@link #MELEE_REACH}, this is how far away it is willing to start running in.
     */
    public static final double MELEE_SWITCH_DISTANCE = 8.0D;

    private static final double RADIUS_PER_SCORE = 0.362D;
    private static final double RADIUS_BASE = 10.5D;
    private static final double MIN_ENGAGEMENT = 30.0D;
    private static final double MAX_ENGAGEMENT = 500.0D;
    /** A mob that is already attacking a player or a humanoid outranks raw value. */
    private static final double HOSTILITY_BONUS = 1000.0D;

    /**
     * A hostile mob that is not neutral: the only kind a humanoid goes looking for on its own. A neutral mob that
     * happens to implement {@link Enemy} (an Enderman, a zombified piglin) is explicitly not included.
     */
    public static boolean isHostileEnemy(LivingEntity target) {
        return target instanceof Enemy && !(target instanceof NeutralMob);
    }

    /** Rough "how dangerous is this" number, dominated by health and attack damage. */
    public static double score(LivingEntity target) {
        return target.getMaxHealth()
                + attribute(target, Attributes.ATTACK_DAMAGE) * 10.0D
                + attribute(target, Attributes.ARMOR) * 2.0D;
    }

    /** How dangerous the target is: the radius a humanoid would clear it at if it were a legitimate target. */
    public static double threatRadius(LivingEntity target) {
        return Mth.clamp(RADIUS_BASE + RADIUS_PER_SCORE * score(target), MIN_ENGAGEMENT, MAX_ENGAGEMENT);
    }

    /**
     * How far away this target may be engaged. Anything that attacked one of ours is engaged at least within the
     * rescue radius; a hostile mob is engaged at its own threat radius.
     */
    public static double engagementRadius(LivingEntity target) {
        double threat = threatRadius(target);
        return threatensAllies(target) ? Math.max(ALLY_RESCUE_RADIUS, threat) : threat;
    }

    /**
     * Selection order. Anything already fighting a player or one of us wins outright: answering a call for help comes
     * before clearing a Warden that is merely standing around.
     */
    public static double priority(LivingEntity target) {
        return priority(null, target);
    }

    /** Ranked with the humanoid's own position, so the nearest threat wins among equally urgent ones. */
    public static double priority(LivingEntity user, LivingEntity target) {
        double value = score(target);
        if (threatensAllies(target)) {
            value += HOSTILITY_BONUS;
        }
        // Next, what is standing on the player's toes. An enemy a few blocks from a player is an immediate problem even
        // if it has not attacked yet, and one that is locked onto a player has already been counted above.
        double value2 = value + playerProximityBonus(target);
        // Finally, distance from the humanoid itself: among equally urgent targets, the nearest is the one to shoot. Without
        // this it could pick something far away and let a closer enemy walk into it untouched.
        if (user == null) {
            return value2;
        }
        double distanceSqr = user.distanceToSqr(target);
        return value2 + NEAREST_BONUS * Math.max(0.0D, 1.0D - distanceSqr / (NEAREST_RADIUS * NEAREST_RADIUS));
    }

    /** Higher the closer the target is to a player, zero beyond {@link #GUARD_RADIUS}. */
    private static double playerProximityBonus(LivingEntity target) {
        Player nearest = target.level().getNearestPlayer(target, GUARD_RADIUS);
        if (nearest == null) {
            return 0.0D;
        }
        double distance = Math.sqrt(target.distanceToSqr(nearest));
        return PROXIMITY_BONUS * Math.max(0.0D, 1.0D - distance / GUARD_RADIUS);
    }

    /** Radius around a player inside which enemies are considered urgent. */
    private static final double GUARD_RADIUS = 24.0D;
    /** Weight of that urgency, chosen to outweigh raw target value but not a call for help. */
    private static final double PROXIMITY_BONUS = 600.0D;
    /** Weight of being close to the humanoid itself. */
    private static final double NEAREST_BONUS = 400.0D;
    /** Distance over which that weight falls to zero. */
    private static final double NEAREST_RADIUS = 32.0D;

    /** True when the mob is currently going after a player, or after one of the humanoids. */
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
