package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.registry.ModEffects;
import com.curiats.returncapsule.util.TrueDamage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShockManager {
    private static final Map<UUID, Target> TARGETS = new HashMap<>();
    private static final Map<UUID, StunTarget> STUNS = new HashMap<>();
    private static final int INITIAL_STUN_PADDING = 5;

    public static void impact(ServerLevel level, Vec3 origin, double half,
                              @Nullable Entity source, Set<UUID> excluded) {
        AABB area = new AABB(origin.x - half, origin.y - half, origin.z - half,
                origin.x + half, origin.y + half, origin.z + half);
        int stunTicks = Config.SHOCK_STUN_TICKS.get();
        float impactDamage = Config.SHOCK_DAMAGE.get().floatValue();
        float occludedMultiplier = Config.SHOCK_OCCLUDED_MULTIPLIER.get().floatValue();

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!target.isAlive() || excluded.contains(target.getUUID())) {
                continue;
            }
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            boolean occluded = isOccluded(level, origin, target);
            float multiplier = occluded ? occludedMultiplier : 1.0F;
            applyStun(target, stunTicks + INITIAL_STUN_PADDING);
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().explosion(source, source), impactDamage * multiplier);
            if (!target.isAlive()) {
                continue;
            }
            target.stopRiding();
            TARGETS.put(target.getUUID(), new Target(target,
                    new Vec3(target.getX(), target.getY(), target.getZ()),
                    level.getGameTime(), multiplier));
        }
    }

    public static boolean isActive(UUID uuid) {
        return TARGETS.containsKey(uuid);
    }

    public static void clear() {
        TARGETS.clear();
        STUNS.clear();
    }

     
    public static void applyShock(LivingEntity target, int ticks) {
        if (ticks <= 0 || target.level().isClientSide || !target.isAlive()) {
            return;
        }
        long now = target.level().getGameTime();
        STUNS.put(target.getUUID(), new StunTarget(target,
                new Vec3(target.getX(), target.getY(), target.getZ()), now + ticks));
        applyStun(target, ticks + 2);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (TARGETS.isEmpty() && STUNS.isEmpty())) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        int stunTicks = Config.SHOCK_STUN_TICKS.get();
        int refreshTicks = Math.max(1, Config.SHOCK_REFRESH_TICKS.get());
        float tickDamage = Config.SHOCK_TICK_DAMAGE.get().floatValue();
        float finishDamage = Config.TRUE_DAMAGE.get().floatValue();

        Iterator<Map.Entry<UUID, Target>> iterator = TARGETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Target target = iterator.next().getValue();
            LivingEntity living = target.entity();
            if (living.isRemoved() || !living.isAlive()
                    || !(living.level() instanceof ServerLevel level)) {
                iterator.remove();
                continue;
            }
            if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
                iterator.remove();
                living.removeEffect(ModEffects.SHOCK.get());
                continue;
            }
            long elapsed = level.getGameTime() - target.startTick();
            if (elapsed > stunTicks) {
                iterator.remove();
                living.removeEffect(ModEffects.SHOCK.get());
                continue;
            }
            if (elapsed % refreshTicks == 0L) {
                applyStun(living, refreshTicks + 2);
            }
            holdStill(living, target.anchor());
            if (elapsed > 0L) {
                living.invulnerableTime = 0;
                living.hurt(level.damageSources().generic(), tickDamage * target.multiplier());
            }
            if (elapsed >= stunTicks) {
                living.invulnerableTime = 0;
                living.hurt(TrueDamage.source(level, null, null), finishDamage * target.multiplier());
                iterator.remove();
                living.removeEffect(ModEffects.SHOCK.get());
            }
        }

        int shockRefresh = Math.max(1, Config.SHOCK_REFRESH_TICKS.get());
        Iterator<Map.Entry<UUID, StunTarget>> stuns = STUNS.entrySet().iterator();
        while (stuns.hasNext()) {
            StunTarget stun = stuns.next().getValue();
            LivingEntity living = stun.entity();
            if (living.isRemoved() || !living.isAlive()
                    || !(living.level() instanceof ServerLevel level)) {
                stuns.remove();
                continue;
            }
            if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
                stuns.remove();
                living.removeEffect(ModEffects.SHOCK.get());
                continue;
            }
            long now = level.getGameTime();
            if (now >= stun.endTick()) {
                stuns.remove();
                living.removeEffect(ModEffects.SHOCK.get());
                continue;
            }
            holdStill(living, stun.anchor());
            if (now % shockRefresh == 0L) {
                applyStun(living, shockRefresh + 2);
            }
        }
    }

    private static void applyStun(LivingEntity target, int durationTicks) {
        target.addEffect(new MobEffectInstance(ModEffects.SHOCK.get(), durationTicks, 0,
                false, true, true));
    }

    private static void holdStill(LivingEntity living, Vec3 anchor) {
        living.setDeltaMovement(Vec3.ZERO);
        living.setSprinting(false);
        living.setSwimming(false);
        if (living instanceof ServerPlayer player) {
            player.getAbilities().flying = false;
            player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
        } else {
            living.setPos(anchor.x, anchor.y, anchor.z);
        }
    }

    private static boolean isOccluded(ServerLevel level, Vec3 origin, LivingEntity target) {
        Vec3 end = target.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() != HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(origin) < origin.distanceToSqr(end) - 0.01D;
    }

    private record Target(LivingEntity entity, Vec3 anchor, long startTick, float multiplier) {
    }

    private record StunTarget(LivingEntity entity, Vec3 anchor, long endTick) {
    }

    private ShockManager() {
    }
}
