package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomEntity;
import com.curiats.returncapsule.entity.fall.SuperBigCup;
import com.curiats.returncapsule.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The base humanoid's two defensive skills.
 *
 * <p>A swarm is what kills a base humanoid: one melee target at a time cannot clear the bodies pressed against it. Both
 * skills answer that directly.</p>
 *
 * <ul>
 *   <li>Grenade burst: when surrounded, six grenades leave the hand in the same tick and land about a quarter of a
 *       second apart. Each is a normal explosion plus a fixed slice of true damage, and the spread deliberately
 *       includes its own position, so the blast clears the ring at the cost of some self harm.</li>
 *   <li>Cruise strike: once every three minutes it marks the nearest tough enemy standing under open sky, and a cruise
 *       bomb homes onto that mark.</li>
 * </ul>
 */
public final class HumanoidSkills {

    private static final int GRENADE_COOLDOWN_TICKS = 20 * 20;

    private static final int CRUISE_COOLDOWN_TICKS = 3 * 60 * 20;

    private static final int BURST_COUNT = 6;

    /** Fuse before the whole handful goes off, just under two seconds. */
    private static final int BURST_FUSE_TICKS = 34;

    private static final double BLAST_RADIUS = 4.0D;

    /** True damage per blast as a share of the target's max health, matching the blade's percentage model. */
    private static final double TRUE_DAMAGE_SHARE = 0.03D;

    private static final double MIN_TRUE_DAMAGE = 4.0D;

    private static final double SWARM_RADIUS = 6.0D;

    private static final int SWARM_COUNT = 3;

    private static final double CRUISE_RANGE = 64.0D;

    private static final double CRUISE_MIN_HEALTH = 150.0D;

    private static final Map<Integer, Integer> GRENADE_COOLDOWN = new ConcurrentHashMap<>();

    private static final Map<Integer, Integer> CRUISE_COOLDOWN = new ConcurrentHashMap<>();

    /** Targets that already have a cruise bomb on the way, so a group does not all hit the same body. */
    private static final Map<java.util.UUID, Long> INCOMING = new ConcurrentHashMap<>();

    /** How long a target stays reserved; long enough to cover the descent from the launch height. */
    private static final int STRIKE_RESERVE_TICKS = 300;

    private static final Map<Integer, List<Vec3>> BURST = new ConcurrentHashMap<>();

    private static final Map<Integer, Integer> BURST_FUSE = new ConcurrentHashMap<>();

    private HumanoidSkills() {
    }

    public static void tick(NeutralHumanoid humanoid) {
        if (humanoid.level().isClientSide || humanoid.isDowned() || !humanoid.isAlive()) {
            forget(humanoid.getId());
            return;
        }
        tickBurst(humanoid);
        tickCooldowns(humanoid);
        tickCruise(humanoid);
    }

    private static void tickCooldowns(NeutralHumanoid humanoid) {
        int grenade = GRENADE_COOLDOWN.getOrDefault(humanoid.getId(), 0);
        if (grenade > 0) {
            GRENADE_COOLDOWN.put(humanoid.getId(), grenade - 1);
        }
        int cruise = CRUISE_COOLDOWN.getOrDefault(humanoid.getId(), 0);
        if (cruise > 0) {
            CRUISE_COOLDOWN.put(humanoid.getId(), cruise - 1);
        }
    }

    private static void tickBurst(NeutralHumanoid humanoid) {
        List<Vec3> points = BURST.get(humanoid.getId());
        if (points == null) {
            if (GRENADE_COOLDOWN.getOrDefault(humanoid.getId(), 0) > 0) {
                return;
            }
            // Entity scans are not free; a swarm does not appear within one tick, so sample every fifth tick.
            if ((humanoid.tickCount + humanoid.getId()) % 5 != 0) {
                return;
            }
            if (!surrounded(humanoid) || !(humanoid.level() instanceof ServerLevel level)) {
                return;
            }
            points = buildBurst(humanoid);
            BURST.put(humanoid.getId(), points);
            // All six leave the hand in this tick; the blasts land together after the fuse.
            for (Vec3 point : points) {
                HumanoidGrenadier.throwAtPoint(humanoid, point);
            }
            level.playSound(null, humanoid.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.4F);
            BURST_FUSE.put(humanoid.getId(), BURST_FUSE_TICKS);
            ReturnCapsuleMod.LOGGER.info("[skill] grenade burst {} x{}", humanoid.getName().getString(), points.size());
            return;
        }
        int fuse = BURST_FUSE.getOrDefault(humanoid.getId(), 0) - 1;
        if (fuse > 0) {
            BURST_FUSE.put(humanoid.getId(), fuse);
            return;
        }
        // One blast per tick from here: the handful lands almost together instead of all in a single frame.
        Vec3 point = points.remove(0);
        detonateOne(humanoid, point);
        if (!points.isEmpty()) {
            BURST_FUSE.put(humanoid.getId(), 0);
            return;
        }
        BURST.remove(humanoid.getId());
        BURST_FUSE.remove(humanoid.getId());
        GRENADE_COOLDOWN.put(humanoid.getId(), GRENADE_COOLDOWN_TICKS);
    }

    /** A normal explosion plus a fixed slice of true damage; self harm is intended. */
    private static void detonateOne(NeutralHumanoid humanoid, Vec3 point) {
        if (!(humanoid.level() instanceof ServerLevel level)) {
            return;
        }
        level.explode(null, point.x, point.y, point.z, (float) BLAST_RADIUS, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        net.minecraft.world.damagesource.DamageSource source = com.curiats.returncapsule.util.TrueDamage
                .boom(level, humanoid, humanoid);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(point, point).inflate(BLAST_RADIUS))) {
            if (!other.isAlive()) {
                continue;
            }
            // Its own blast is fair game, but it is not supposed to shred the rest of its squad.
            if (other instanceof NeutralHumanoid && other != humanoid) {
                continue;
            }
            if (Math.sqrt(other.distanceToSqr(point)) > BLAST_RADIUS) {
                continue;
            }
            other.invulnerableTime = 0;
            // Same model as every other base humanoid hit: a share of the target's own max health, not a flat number.
            other.hurt(source, (float) Math.max(MIN_TRUE_DAMAGE, other.getMaxHealth() * TRUE_DAMAGE_SHARE));
        }
    }

    /** True when enough enemies are pressed close that a melee humanoid cannot work through them one by one. */
    private static boolean surrounded(NeutralHumanoid humanoid) {
        int close = 0;
        for (LivingEntity other : humanoid.level().getEntitiesOfClass(LivingEntity.class,
                humanoid.getBoundingBox().inflate(SWARM_RADIUS))) {
            if (other == humanoid || !(other instanceof Enemy) || !other.isAlive()) {
                continue;
            }
            close++;
        }
        return close >= SWARM_COUNT;
    }

    /** Six impact points in a ring around the humanoid; it is allowed to catch its own blast. */
    private static List<Vec3> buildBurst(NeutralHumanoid humanoid) {
        List<Vec3> points = new ArrayList<>(BURST_COUNT);
        double start = humanoid.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int index = 0; index < BURST_COUNT; index++) {
            double angle = start + index * (Math.PI * 2.0D / BURST_COUNT)
                    + (humanoid.getRandom().nextDouble() - 0.5D) * 0.4D;
            double radius = 2.5D + humanoid.getRandom().nextDouble() * 3.0D;
            points.add(new Vec3(
                    humanoid.getX() + Math.cos(angle) * radius,
                    humanoid.getY() + 0.2D,
                    humanoid.getZ() + Math.sin(angle) * radius));
        }
        return points;
    }

    private static void tickCruise(NeutralHumanoid humanoid) {
        if (CRUISE_COOLDOWN.getOrDefault(humanoid.getId(), 0) > 0) {
            return;
        }
        if (!(humanoid.level() instanceof ServerLevel level)) {
            return;
        }
        // A sky check per candidate is not cheap; the skill has a three minute cadence, so look once a second.
        if ((humanoid.tickCount + humanoid.getId()) % 20 != 0) {
            return;
        }
        LivingEntity mark = findCruiseMark(level, humanoid);
        if (mark == null) {
            return;
        }
        if (!launch(level, humanoid, mark)) {
            return;
        }
        CRUISE_COOLDOWN.put(humanoid.getId(), CRUISE_COOLDOWN_TICKS);
    }

    private static LivingEntity findCruiseMark(ServerLevel level, NeutralHumanoid humanoid) {
        LivingEntity best = null;
        double bestDistance = CRUISE_RANGE * CRUISE_RANGE;
        long now = level.getGameTime();
        INCOMING.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                humanoid.getBoundingBox().inflate(CRUISE_RANGE))) {
            if (other == humanoid || !(other instanceof Enemy) || !other.isAlive()) {
                continue;
            }
            if (other.getMaxHealth() <= CRUISE_MIN_HEALTH) {
                continue;
            }
            if (!level.canSeeSky(other.blockPosition())) {
                continue;
            }
            if (INCOMING.getOrDefault(other.getUUID(), 0L) > now) {
                // Someone already has a bomb on this one; leave the cooldown for a free target.
                continue;
            }
            double distance = other.distanceToSqr(humanoid);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private static boolean launch(ServerLevel level, NeutralHumanoid humanoid, LivingEntity mark) {
        try {
            // Keep the launch close to the humanoid: passing the strike range as the "player distance" pushed the
            // launch point hundreds of blocks away, outside client tracking, so the bomb and its marker never showed.
            double approach = Math.min(32.0D, Math.max(8.0D, humanoid.distanceTo(mark)));
            SuperBigCup controller = SuperBigCup.create(level, mark.getBoundingBox().getCenter(), mark.getUUID(),
                    SuperBigCup.nextIncidence(), approach, humanoid.position(), null, level.random);
            level.getChunkAt(BlockPos.containing(controller.launchPoint()));
            BoomEntity bomb = new BoomEntity(level, controller, null);
            if (!level.addFreshEntity(bomb)) {
                return false;
            }
            bomb.release();
            mark.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            INCOMING.put(mark.getUUID(), level.getGameTime() + STRIKE_RESERVE_TICKS);
            level.playSound(null, humanoid.blockPosition(), ModSounds.MISSILE_LOCK.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
            ReturnCapsuleMod.LOGGER.info("[skill] cruise strike {} -> {} ({})",
                    humanoid.getName().getString(), mark.getName().getString(), mark.getMaxHealth());
            return true;
        } catch (RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.warn("[skill] cruise strike failed", exception);
            return false;
        }
    }

    private static void forget(int id) {
        GRENADE_COOLDOWN.remove(id);
        CRUISE_COOLDOWN.remove(id);
        BURST.remove(id);
        BURST_FUSE.remove(id);
    }
}
