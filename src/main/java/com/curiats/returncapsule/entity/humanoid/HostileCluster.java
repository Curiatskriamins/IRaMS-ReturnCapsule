package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HostileCluster {

    private static final int CELL = 6;
    private static final int MIN_MEMBERS = 5;
    private static final int MAX_MEMBERS = 64;

    private final AABB box;
    private final Vec3 center;
    private final int members;

    private HostileCluster(AABB box, Vec3 center, int members) {
        this.box = box;
        this.center = center;
        this.members = members;
    }

    public AABB box() {
        return this.box;
    }

    public Vec3 center() {
        return this.center;
    }

    public int members() {
        return this.members;
    }

    public boolean contains(LivingEntity entity) {
        return this.box.contains(entity.position());
    }

    public static HostileCluster build(Level level, Vec3 origin, double radius, Mob viewer) {
        List<LivingEntity> hostiles = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, origin).inflate(radius), candidate -> candidate instanceof net.minecraft.world.entity.monster.Enemy
                        && candidate.isAlive() && !(candidate instanceof NeutralHumanoid));
        if (hostiles.size() < MIN_MEMBERS) {
            return null;
        }
        Map<Long, List<LivingEntity>> buckets = new HashMap<>();
        for (LivingEntity hostile : hostiles) {
            long key = cellKey(hostile);
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(hostile);
        }
        List<LivingEntity> best = null;
        for (List<LivingEntity> bucket : buckets.values()) {
            if (bucket.size() >= MIN_MEMBERS && (best == null || bucket.size() > best.size())) {
                best = bucket;
            }
        }
        if (best == null) {
            return null;
        }
        int limit = Math.min(best.size(), MAX_MEMBERS);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int index = 0; index < limit; index++) {
            AABB memberBox = best.get(index).getBoundingBox();
            minX = Math.min(minX, memberBox.minX);
            minY = Math.min(minY, memberBox.minY);
            minZ = Math.min(minZ, memberBox.minZ);
            maxX = Math.max(maxX, memberBox.maxX);
            maxY = Math.max(maxY, memberBox.maxY);
            maxZ = Math.max(maxZ, memberBox.maxZ);
        }
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1.0D);
        return new HostileCluster(box, box.getCenter(), limit);
    }

    public static int countInside(Level level, AABB box) {
        return level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate instanceof net.minecraft.world.entity.monster.Enemy
                        && candidate.isAlive() && !(candidate instanceof NeutralHumanoid)).size();
    }

    private static long cellKey(LivingEntity entity) {
        long x = Math.floorDiv((int) Math.floor(entity.getX()), CELL);
        long y = Math.floorDiv((int) Math.floor(entity.getY()), CELL);
        long z = Math.floorDiv((int) Math.floor(entity.getZ()), CELL);
        return (x & 0x1FFFFF) << 42 | (y & 0x1FFFFF) << 21 | (z & 0x1FFFFF);
    }
}
