package com.curiats.returncapsule.event;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.BossBars;
import com.curiats.returncapsule.item.StandardStorageAnchorItem;
import com.curiats.returncapsule.registry.ModEffects;
import com.curiats.returncapsule.registry.ModItems;
import com.curiats.returncapsule.util.TickingContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForcedDisplacementGuard {

    private static final int CONTROL_TICKS = 10;

    private static final float DRAIN_PER_TICK = 0.10F;

    private static final int HOSTILE_WINDOW_TICKS = 60;

    private static final double TRACE_RADIUS = 16.0D;

    private static final Map<UUID, Integer> TICKS = new ConcurrentHashMap<>();

    private static final Map<UUID, LivingEntity> SOURCES = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> HOSTILE_TOUCH = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> WHITELIST_UNTIL = new ConcurrentHashMap<>();

    private static final Map<UUID, LivingEntity> LAST_ATTACKER = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> CONTROL_DEADLINE = new ConcurrentHashMap<>();

    private static final Map<UUID, Boolean> FROZEN = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> GUARD_TICK = new ConcurrentHashMap<>();

    private static final Map<UUID, Boolean> GUARD_VALUE = new ConcurrentHashMap<>();

    private static final AtomicBoolean REPORTED_MISSING_CONTEXT = new AtomicBoolean();

    private ForcedDisplacementGuard() {
    }

    public record Trace(LivingEntity source, boolean exact) {
        public static final Trace NONE = new Trace(null, false);
    }

    public static boolean hasGuard(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        long now = player.level().getGameTime();
        Long cachedTick = GUARD_TICK.get(player.getUUID());
        if (cachedTick != null && cachedTick == now) {
            return Boolean.TRUE.equals(GUARD_VALUE.get(player.getUUID()));
        }
        boolean value = computeGuard(player, now);
        GUARD_TICK.put(player.getUUID(), now);
        GUARD_VALUE.put(player.getUUID(), value);
        return value;
    }

    private static boolean computeGuard(Player player, long now) {
        if (player.isSpectator() || !Config.anchorGuardEnabled()) {
            return false;
        }
        Long allowed = WHITELIST_UNTIL.get(player.getUUID());
        if (allowed != null && now < allowed) {
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.STANDARD_STORAGE_ANCHOR.get()) && StandardStorageAnchorItem.guardEnabled(stack)) {
                return true;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModItems.STANDARD_STORAGE_ANCHOR.get()) && StandardStorageAnchorItem.guardEnabled(offhand);
    }

    public static boolean hostileRecently(LivingEntity entity) {
        Long when = HOSTILE_TOUCH.get(entity.getUUID());
        return when != null && entity.level().getGameTime() - when <= HOSTILE_WINDOW_TICKS;
    }

    public static Trace traceSource(LivingEntity victim) {
        var actor = TickingContext.current();
        if (actor == null && REPORTED_MISSING_CONTEXT.compareAndSet(false, true)) {
            ReturnCapsuleMod.LOGGER.debug("[guard] tick context unavailable, forced displacement tracing falls back "
                    + "to heuristics; the guards will only block, never punish");
        }
        if (actor instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner
                && owner != victim && owner instanceof Enemy && owner.isAlive()) {
            return new Trace(owner, true);
        }
        if (actor instanceof LivingEntity living && living != victim && living instanceof Enemy && living.isAlive()) {
            return new Trace(living, true);
        }
        LivingEntity remembered = LAST_ATTACKER.get(victim.getUUID());
        if (validSource(victim, remembered)) {
            return new Trace(remembered, false);
        }
        LivingEntity attacker = victim.getLastHurtByMob();
        if (validSource(victim, attacker)) {
            return new Trace(attacker, false);
        }
        if (!(victim.level() instanceof ServerLevel level)) {
            return Trace.NONE;
        }
        AABB area = victim.getBoundingBox().inflate(TRACE_RADIUS);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, area, mob -> mob instanceof Enemy && mob.isAlive());
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob mob : nearby) {
            if (mob.getTarget() != victim && mob.getLastHurtByMob() != victim) {
                continue;
            }
            double distance = mob.distanceToSqr(victim);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best == null ? Trace.NONE : new Trace(best, false);
    }

    private static boolean validSource(LivingEntity victim, LivingEntity source) {
        return source != null
                && source != victim
                && source.isAlive()
                && source instanceof Enemy
                && source.distanceToSqr(victim) <= 1024.0D;
    }

    public static void begin(LivingEntity victim, Trace trace) {
        if (victim.level().isClientSide || !hasGuard(victim) || trace.source() == null) {
            return;
        }
        if (!trace.exact()) {
            return;
        }
        var typeId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(trace.source().getType());
        if (BossBars.hasBar(trace.source())
                || (typeId != null && Config.anchorGuardBoss(typeId.toString()))) {
            return;
        }
        TICKS.put(trace.source().getUUID(), CONTROL_TICKS);
        CONTROL_DEADLINE.put(trace.source().getUUID(),
                trace.source().level().getGameTime() + CONTROL_TICKS * 4L);
        SOURCES.put(trace.source().getUUID(), trace.source());
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (event instanceof EntityTeleportEvent.TeleportCommand) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim) || victim.level().isClientSide) {
            return;
        }
        if (!hasGuard(victim) || !hostileRecently(victim)) {
            return;
        }
        if (victim instanceof net.minecraft.server.level.ServerPlayer player && player.isChangingDimension()) {
            return;
        }
        Trace trace = traceSource(victim);
        if (!trace.exact()) {
            return;
        }
        event.setCanceled(true);
        begin(victim, trace);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !hasGuard(victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !(attacker instanceof Enemy)
                || !attacker.isAlive()) {
            return;
        }
        HOSTILE_TOUCH.put(victim.getUUID(), victim.level().getGameTime());
        LAST_ATTACKER.put(victim.getUUID(), attacker);
        if (!Config.anchorGuardReflect()) {
            return;
        }
        float cap = victim.getMaxHealth() * 0.05F;
        if (event.getAmount() <= cap) {
            return;
        }
        float remaining = attacker.getHealth() - event.getAmount();
        if (remaining <= 0.0F) {
            attacker.die(event.getSource());
        } else {
            attacker.setHealth(remaining);
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        tickControl(entity);
    }

    private static void tickControl(LivingEntity candidate) {
        Integer remaining = TICKS.get(candidate.getUUID());
        if (remaining == null) {
            return;
        }
        LivingEntity source = SOURCES.get(candidate.getUUID());
        Long deadline = CONTROL_DEADLINE.get(candidate.getUUID());
        if (deadline != null && candidate.level().getGameTime() > deadline) {
            TICKS.remove(candidate.getUUID());
            SOURCES.remove(candidate.getUUID());
            CONTROL_DEADLINE.remove(candidate.getUUID());
            FROZEN.remove(candidate.getUUID());
            if (source instanceof Mob mob) {
                mob.setNoAi(false);
            }
            return;
        }
        if (source == null || source.isRemoved() || source.isDeadOrDying()) {
            TICKS.remove(candidate.getUUID());
            SOURCES.remove(candidate.getUUID());
            releaseFreeze(candidate);
            return;
        }
        if (remaining <= 0) {
            TICKS.remove(candidate.getUUID());
            SOURCES.remove(candidate.getUUID());
            CONTROL_DEADLINE.remove(candidate.getUUID());
            releaseFreeze(source);
            return;
        }
        TICKS.put(candidate.getUUID(), remaining - 1);
        source.setDeltaMovement(0.0D, 0.0D, 0.0D);
        source.hurtMarked = true;
        if (source instanceof Mob mob) {
            mob.setNoAi(true);
            FROZEN.put(source.getUUID(), true);
        } else {
            source.addEffect(new MobEffectInstance(ModEffects.SHOCK.get(), 20, 0, false, false, false));
        }
        float drain = source.getMaxHealth() * DRAIN_PER_TICK;
        float next = source.getHealth() - drain;
        source.setHealth(Math.max(0.0F, next));
        if (next <= 0.0F) {
            TICKS.remove(source.getUUID());
            SOURCES.remove(source.getUUID());
            releaseFreeze(source);
            source.die(source.damageSources().generic());
            if (!source.isRemoved()) {
                source.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            }
        }
    }

    private static void releaseFreeze(LivingEntity entity) {
        if (FROZEN.remove(entity.getUUID()) != null && entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
                || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        java.util.Iterator<java.util.Map.Entry<UUID, Long>> iterator = CONTROL_DEADLINE.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<UUID, Long> entry = iterator.next();
            LivingEntity source = SOURCES.get(entry.getKey());
            if (source == null) {
                iterator.remove();
                continue;
            }
            if (source.level().getGameTime() <= entry.getValue()) {
                continue;
            }
            iterator.remove();
            TICKS.remove(entry.getKey());
            SOURCES.remove(entry.getKey());
            if (FROZEN.remove(entry.getKey()) != null && source instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        UUID id = entity.getUUID();
        TICKS.remove(id);
        SOURCES.remove(id);
        CONTROL_DEADLINE.remove(id);
        HOSTILE_TOUCH.remove(id);
        WHITELIST_UNTIL.remove(id);
        LAST_ATTACKER.remove(id);
        GUARD_TICK.remove(id);
        GUARD_VALUE.remove(id);
        releaseFreeze(entity);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        TICKS.remove(id);
        SOURCES.remove(id);
        CONTROL_DEADLINE.remove(id);
        HOSTILE_TOUCH.remove(id);
        WHITELIST_UNTIL.remove(id);
        LAST_ATTACKER.remove(id);
        FROZEN.remove(id);
        GUARD_TICK.remove(id);
        GUARD_VALUE.remove(id);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id != null && Config.anchorGuardWhitelisted(id.toString())) {
            WHITELIST_UNTIL.put(player.getUUID(), player.level().getGameTime() + 40L);
        }
    }
}
