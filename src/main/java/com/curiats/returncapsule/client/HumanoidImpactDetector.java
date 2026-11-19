package com.curiats.returncapsule.client;

import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.phys.Vec3;

public final class HumanoidImpactDetector {

    private static final double VELOCITY_JUMP = 1.6D;
    private static final double POSITION_JUMP = 1.1D;

    private static final double TELEPORT_GUARD = 3.0D;
    private static final int COOLDOWN_TICKS = 40;

    private static final java.util.Map<Integer, Vec3> LAST_POSITION = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, Vec3> LAST_VELOCITY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, Integer> COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();

    private HumanoidImpactDetector() {
    }

    public static void reset(int entityId) {
        LAST_POSITION.remove(entityId);
        LAST_VELOCITY.remove(entityId);
        COOLDOWN.remove(entityId);
    }

    public static void tick(NeutralHumanoid humanoid) {
        int id = humanoid.getId();
        if (humanoid.isPassenger()) {
            reset(id);
            return;
        }
        Vec3 position = humanoid.position();
        Vec3 velocity = humanoid.getDeltaMovement();
        Vec3 lastPos = LAST_POSITION.put(id, position);
        Vec3 lastVel = LAST_VELOCITY.put(id, velocity);
        Integer cooldown = COOLDOWN.get(id);
        if (cooldown != null && cooldown > 0) {
            COOLDOWN.put(id, cooldown - 1);
            return;
        }
        if (lastPos == null || lastVel == null) {
            return;
        }
        if (humanoid.isDownedSynced() || humanoid.isLeaping() || humanoid.teleportGrace()) {
            return;
        }
        double velocityJump = velocity.subtract(lastVel).length();
        double positionJump = position.subtract(lastPos).horizontalDistance();
        if (positionJump > TELEPORT_GUARD) {
            return;
        }
        boolean knocked = humanoid.hurtTime > 0;
        // A position jump alone is far too noisy: being shoved by a vehicle or a doorway correction looks identical to
        // an impact. Only treat it as one while the humanoid is actually falling, and keep the velocity branch for real
        // hits.
        boolean impact = velocityJump > VELOCITY_JUMP
                || (humanoid.fallDistance > 1.5F && positionJump > POSITION_JUMP);
        if (impact) {
            COOLDOWN.put(id, COOLDOWN_TICKS);
            com.curiats.returncapsule.network.ModNetwork.sendRagdollStart(id);
        }
    }
}
