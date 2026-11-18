package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.compat.lrt.Throwables;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HumanoidGrenadier {

    private static final int COOLDOWN_TICKS = 100;

    private static final int CALM_THRESHOLD = 40;
    private static final double MIN_RANGE = 5.0D;
    private static final double MAX_RANGE = 22.0D;
    private static final Map<Integer, Integer> COOLDOWN = new ConcurrentHashMap<>();

    private static final Map<Integer, Vec3> PENDING = new ConcurrentHashMap<>();

    private static final Map<Integer, Double> JUMP_START = new ConcurrentHashMap<>();

    private static final double JUMP_APEX = 1.25D;

    private HumanoidGrenadier() {
    }

    public static boolean tryThrow(NeutralHumanoid humanoid, LivingEntity target, boolean sightBlocked) {
        if (humanoid.level().isClientSide || humanoid.isPassenger() || humanoid.isDowned()) {
            return false;
        }
        if (!HumanoidToggles.isEnabled(HumanoidToggles.Slot.RANGED)) {
            return false;
        }
        int cooldown = COOLDOWN.getOrDefault(humanoid.getId(), 0);
        if (cooldown > 0) {
            COOLDOWN.put(humanoid.getId(), cooldown - 1);
            return false;
        }
        double distance = humanoid.distanceTo(target);
        if (distance < MIN_RANGE || distance > MAX_RANGE) {
            return false;
        }
        ItemStack stack = pickThrowable(humanoid, target);
        if (stack.isEmpty()) {
            return false;
        }
        Throwables.ThrowableInfo info = Throwables.describe(stack);
        if (info == null) {
            return false;
        }
        Vec3 pending = PENDING.get(humanoid.getId());
        if (pending != null) {
            double start = JUMP_START.getOrDefault(humanoid.getId(), humanoid.getY());
            if (humanoid.onGround()) {
                PENDING.remove(humanoid.getId());
                return false;
            }
            if (humanoid.getY() - start < JUMP_APEX * 0.7D) {
                return false;
            }
            PENDING.remove(humanoid.getId());
            return release(humanoid, stack, pending, info);
        }
        Vec3 impact = pickImpact(humanoid, target, info, humanoid.getEyePosition());
        if (impact != null) {
            return release(humanoid, stack, impact, info);
        }
        Vec3 apex = humanoid.getEyePosition().add(0.0D, JUMP_APEX, 0.0D);
        impact = pickImpact(humanoid, target, info, apex);
        if (impact == null) {
            stepAside(humanoid, target);
            return false;
        }
        PENDING.put(humanoid.getId(), impact);
        JUMP_START.put(humanoid.getId(), humanoid.getY());
        humanoid.setDeltaMovement(0.0D, 0.42D, 0.0D);
        humanoid.hasImpulse = true;
        return false;
    }

    private static boolean release(NeutralHumanoid humanoid, ItemStack stack, Vec3 impact,
            Throwables.ThrowableInfo info) {
        float[] angles = solveAngles(humanoid.getEyePosition(), impact, info);
        if (angles == null) {
            return false;
        }
        if (!Throwables.throwAt(humanoid.level(), humanoid, stack, angles[0], angles[1])) {
            return false;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            humanoid.getThrowables().remove(stack);
        }
        COOLDOWN.put(humanoid.getId(), COOLDOWN_TICKS);
        return true;
    }

    private static Vec3 pickImpact(NeutralHumanoid humanoid, LivingEntity target,
            Throwables.ThrowableInfo info, Vec3 origin) {
        double spread = Math.max(1.0D, info.radius() * 0.55D);
        Vec3 base = impactPoint(humanoid, target, info);
        Vec3[] candidates = new Vec3[]{
                base,
                base.add(spread, 0.0D, 0.0D),
                base.add(-spread, 0.0D, 0.0D),
                base.add(0.0D, 0.0D, spread),
                base.add(0.0D, 0.0D, -spread),
                base.add(spread, 0.0D, spread),
                base.add(-spread, 0.0D, -spread)
        };
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Vec3 candidate : candidates) {
            if (solveAngles(origin, candidate, info) == null) {
                continue;
            }
            if (!safeBlast(humanoid, candidate, info)) {
                continue;
            }
            double score = -candidate.distanceTo(target.position());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static void stepAside(NeutralHumanoid humanoid, LivingEntity target) {
        double dx = target.getX() - humanoid.getX();
        double dz = target.getZ() - humanoid.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3D) {
            return;
        }
        double px = -dz / length;
        double pz = dx / length;
        double sign = 1.0D;
        net.minecraft.world.entity.player.Player player = humanoid.level().getNearestPlayer(humanoid, 32.0D);
        if (player != null) {
            double dotPlayer = (player.getX() - humanoid.getX()) * px + (player.getZ() - humanoid.getZ()) * pz;
            sign = dotPlayer > 0.0D ? -1.0D : 1.0D;
        }
        double stepX = humanoid.getX() + px * sign * 3.5D;
        double stepZ = humanoid.getZ() + pz * sign * 3.5D;
        net.minecraft.core.BlockPos spot = net.minecraft.core.BlockPos.containing(stepX, humanoid.getY(), stepZ);
        if (HumanoidTraversal.canStandOn(humanoid.level(), spot)) {
            humanoid.requestMove(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.3D, 2);
        }
    }

    private static boolean safeBlast(NeutralHumanoid humanoid, Vec3 impact,
            Throwables.ThrowableInfo info) {
        double radius = info.radius() + 2.5D;
        if (humanoid.position().distanceTo(impact) < radius) {
            return false;
        }
        for (net.minecraft.world.entity.player.Player player : humanoid.level().getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class,
                humanoid.getBoundingBox().inflate(radius + 12.0D))) {
            if (player.position().distanceTo(impact) < radius) {
                return false;
            }
        }
        for (NeutralHumanoid other : humanoid.level().getEntitiesOfClass(NeutralHumanoid.class,
                humanoid.getBoundingBox().inflate(radius + 12.0D))) {
            if (other != humanoid && other.position().distanceTo(impact) < radius) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 impactPoint(NeutralHumanoid humanoid, LivingEntity target, Throwables.ThrowableInfo info) {
        double seconds = info.contact() ? 0.0D : info.lifeTime() / 20.0D;
        Vec3 predicted = target.position().add(target.getDeltaMovement().scale(seconds * 20.0D));
        if (info.contact()) {
            predicted = predicted.add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        } else {
            predicted = predicted.add(0.0D, 0.15D, 0.0D);
        }
        return predicted;
    }

    private static float[] solveAngles(Vec3 from, Vec3 to, Throwables.ThrowableInfo info) {
        Vec3 flat = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
        double distance = flat.length();
        if (distance < 1.0E-3D) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
        double height = to.y - from.y;
        float bestPitch = Float.NaN;
        double bestError = Double.MAX_VALUE;
        for (int pitch = -75; pitch <= 70; pitch += 1) {
            double vx = Math.cos(Math.toRadians(pitch)) * info.initialSpeed();
            double vy = Math.sin(Math.toRadians(pitch)) * info.initialSpeed();
            double px = 0.0D;
            double py = 0.0D;
            boolean crossed = false;
            for (int tick = 0; tick < 140; tick++) {
                px += vx;
                py += vy;
                vx *= 0.99D;
                vy = vy * 0.99D - info.gravity();
                if (py <= height && vy < 0.0D) {
                    crossed = true;
                    break;
                }
            }
            if (!crossed) {
                continue;
            }
            double error = Math.abs(px - distance);
            if (error < bestError) {
                bestError = error;
                bestPitch = pitch;
            }
        }
        if (Float.isNaN(bestPitch) || bestError > 3.0D) {
            return null;
        }
        return new float[]{bestPitch, yaw};
    }

    private static ItemStack pickThrowable(NeutralHumanoid humanoid, LivingEntity target) {
        ItemStack best = ItemStack.EMPTY;
        double bestScore = Double.NEGATIVE_INFINITY;
        double targetSpeed = target.getDeltaMovement().horizontalDistance();
        double health = target.getHealth();
        for (ItemStack candidate : humanoid.getThrowables()) {
            Throwables.ThrowableInfo info = Throwables.describe(candidate);
            if (info == null) {
                continue;
            }
            if (!usable(info, targetSpeed)) {
                continue;
            }
            double score = info.damage() + info.radius();
            if (targetSpeed > 0.15D && info.contact()) {
                score += 40.0D;
            }
            if (health < 20.0D && info.radius() > 4.0F) {
                score -= 10.0D;
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean usable(Throwables.ThrowableInfo info, double targetSpeed) {
        String path = info.id().getPath().toLowerCase(java.util.Locale.ROOT);
        if (path.contains("smoke") || path.contains("flash") || path.contains("stun")
                || path.contains("tear") || path.contains("gas")) {
            return false;
        }
        if (info.cloud() && !(path.contains("molotov") || path.contains("incendiary")
                || path.contains("fire"))) {
            return false;
        }
        if (info.destroysBlocks() || info.remote()) {
            return false;
        }
        if (path.contains("molotov") || path.contains("incendiary")) {
            return targetSpeed < 0.08D;
        }
        return info.explosive() && info.damage() > 0.0D;
    }
}
