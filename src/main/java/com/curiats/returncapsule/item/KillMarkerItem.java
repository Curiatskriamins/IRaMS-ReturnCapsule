package com.curiats.returncapsule.item;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomEntity;
import com.curiats.returncapsule.entity.fall.SuperBigCup;
import com.curiats.returncapsule.registry.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kill indicator: the designator used to call cruise bombs. Its display name deliberately says
 * "kill" so it is never confused with the transport designator used to board a capsule.
 *
 * <p>Holding right click for one second starts marking. While marking, one cruise bomb is fired
 * every second, each one paid for with nine diamonds or one diamond block from the player's
 * inventory. If the player sneaks while marking, the bombs are queued instead of launched, and
 * the whole queue is released at once when the sneak key or the item is released.</p>
 *
 * <p>Anchor type II itself is not a block: every bomb owns its target point inside
 * {@link SuperBigCup} and draws the white particle marker while it flies.</p>
 */
public class KillMarkerItem extends Item {
    private static final int USE_DURATION = 72000;
    private static final Map<UUID, Volley> VOLLEYS = new HashMap<>();

    public KillMarkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(living instanceof ServerPlayer player)) {
            return;
        }
        int elapsed = this.getUseDuration(stack) - remainingUseDuration;
        Volley volley = VOLLEYS.computeIfAbsent(player.getUUID(), id -> new Volley());
        boolean sneaking = player.isShiftKeyDown();

        // Releasing the sneak key dumps the whole queue at once.
        if (volley.sneaking && !sneaking) {
            release(level, player, volley);
            volley.sneaking = false;
            return;
        }
        volley.sneaking = sneaking;

        int charge = Math.max(1, Config.KILL_MARKER_CHARGE_TICKS.get());
        if (elapsed < charge) {
            return;
        }
        int interval = fireInterval(player);
        if ((elapsed - charge) % interval != 0) {
            return;
        }

        int maxQueued = Math.max(1, Config.KILL_MARKER_MAX_QUEUED.get());
        if (sneaking && volley.shots.size() >= maxQueued) {
            return;
        }

        Shot shot = resolveShot(player, level);
        if (shot == null) {
            return;
        }
        Payment payment = pay(player);
        if (payment == null) {
            return;
        }

        double angle = SuperBigCup.nextIncidence();
        if (sneaking) {
            volley.shots.add(new QueuedShot(shot.point(), shot.entity(), angle, payment));
        } else if (!spawnBomb(level, player, shot.point(), shot.entity(), angle, null)) {
            refund(player, payment);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (level.isClientSide || !(living instanceof ServerPlayer player)) {
            return;
        }
        Volley volley = VOLLEYS.remove(player.getUUID());
        if (volley == null) {
            return;
        }
        // A dying player should not release a paid volley into the world; give the materials back.
        if (player.isDeadOrDying()) {
            refundShots(player, volley);
            return;
        }
        release(level, player, volley);
    }

    private static void release(Level level, ServerPlayer player, Volley volley) {
        if (volley.shots.isEmpty()) {
            return;
        }
        Set<Double> reserved = new HashSet<>();
        for (QueuedShot shot : volley.shots) {
            reserved.add(shot.angle());
        }
        for (QueuedShot shot : volley.shots) {
            if (!spawnBomb(level, player, shot.point(), shot.entity(), shot.angle(), reserved)) {
                refund(player, shot.payment());
            }
        }
        volley.shots.clear();
    }

    private static boolean spawnBomb(Level level, ServerPlayer player, Vec3 point,
                                     @Nullable UUID entity, double angle,
                                     @Nullable Set<Double> reservedAngles) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        SuperBigCup controller;
        BoomEntity bomb;
        try {
            double playerDistance = Math.sqrt(player.distanceToSqr(point));
            controller = SuperBigCup.create(server, point, entity, angle, playerDistance,
                    player.position(), reservedAngles, server.random);
            bomb = new BoomEntity(server, controller, player);
        } catch (RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.warn("Failed to build a cruise bomb", exception);
            return false;
        }
        if (!server.addFreshEntity(bomb)) {
            return false;
        }
        if (reservedAngles != null) {
            reservedAngles.add(controller.incidenceDegrees());
        }
        if (entity != null && server.getEntity(entity) instanceof LivingEntity) {
            // The client keeps a single clock instance alive, so a whole volley cannot stack it.
            player.playNotifySound(ModSounds.MISSILE_LOCK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return true;
    }

    /**
     * The fire interval grows when the server simulation distance is below the configured minimum:
     * the loaded edge is closer, so the next bomb waits longer before it is released.
     */
    private static int fireInterval(ServerPlayer player) {
        int interval = Math.max(1, Config.KILL_MARKER_INTERVAL_TICKS.get());
        int minimum = Math.max(1, Config.KILL_MARKER_MIN_SIMULATION_CHUNKS.get());
        int simulation = Math.max(1, player.getServer().getPlayerList().getSimulationDistance());
        if (simulation < minimum) {
            interval += (minimum - simulation) * 20;
        }
        return interval;
    }

    /** Charges the player for one bomb, or returns {@code null} when they cannot pay. */
    @Nullable
    private static Payment pay(ServerPlayer player) {
        if (player.isCreative()) {
            return Payment.FREE;
        }
        Inventory inventory = player.getInventory();
        int diamonds = Math.max(1, Config.KILL_MARKER_DIAMONDS.get());
        if (count(inventory, Items.DIAMOND) >= diamonds) {
            consume(inventory, Items.DIAMOND, diamonds);
            return new Payment(Items.DIAMOND, diamonds);
        }
        int blocks = Math.max(1, Config.KILL_MARKER_DIAMOND_BLOCKS.get());
        if (count(inventory, Items.DIAMOND_BLOCK) >= blocks) {
            consume(inventory, Items.DIAMOND_BLOCK, blocks);
            return new Payment(Items.DIAMOND_BLOCK, blocks);
        }
        return null;
    }

    /** Returns a failed launch's payment so the player never loses materials for nothing. */
    private static void refund(ServerPlayer player, Payment payment) {
        if (payment.item() == null || payment.count() <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(payment.item(), payment.count());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int i = 0, size = inventory.getContainerSize(); i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consume(Inventory inventory, Item item, int amount) {
        int left = amount;
        for (int i = 0, size = inventory.getContainerSize(); i < size && left > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        inventory.setChanged();
    }

    /** Resolves the crosshair into a lock target, or {@code null} when it points at nothing. */
    @Nullable
    private static Shot resolveShot(ServerPlayer player, Level level) {
        // Only mark targets that are inside the simulation radius: the bomb spawns at that edge,
        // and a bomb in a loaded but non-ticking chunk would never move.
        int simulation = Math.max(1, player.getServer().getPlayerList().getSimulationDistance());
        int margin = Config.BOOM_SPAWN_MARGIN_CHUNKS.get();
        double simulationLimit = Math.max(8.0D, (simulation - margin) * 16.0D - 16.0D);
        double range = Math.min(Config.KILL_MARKER_RANGE.get(), simulationLimit);

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
        AABB search = new AABB(eye, end).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, end, search,
                entity -> entity != player && entity.isAlive() && !entity.isSpectator()
                        && entity.isPickable());
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (entityHit != null && (blockHit.getType() == HitResult.Type.MISS
                || entityHit.getLocation().distanceToSqr(eye)
                <= blockHit.getLocation().distanceToSqr(eye))) {
            Entity entity = entityHit.getEntity();
            return new Shot(entity.getBoundingBox().getCenter(), entity.getUUID());
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            Direction face = blockHit.getDirection();
            Vec3 point = blockHit.getLocation().add(
                    face.getStepX() * 0.05D, face.getStepY() * 0.05D, face.getStepZ() * 0.05D);
            return new Shot(point, null);
        }
        return null;
    }

    /** Returns every paid but unlaunched shot to the player and clears the volley. */
    public static void refundVolley(ServerPlayer player) {
        Volley volley = VOLLEYS.remove(player.getUUID());
        if (volley != null) {
            refundShots(player, volley);
        }
    }

    private static void refundShots(ServerPlayer player, Volley volley) {
        for (QueuedShot shot : volley.shots) {
            refund(player, shot.payment());
        }
        volley.shots.clear();
    }

    /** Drops a pending sneak volley; called when the player leaves, dies or respawns. */
    public static void dropVolley(UUID playerId) {
        VOLLEYS.remove(playerId);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    private record Shot(Vec3 point, @Nullable UUID entity) {
    }

    private record QueuedShot(Vec3 point, @Nullable UUID entity, double angle, Payment payment) {
    }

    private record Payment(@Nullable Item item, int count) {
        private static final Payment FREE = new Payment(null, 0);
    }

    private static final class Volley {
        private final List<QueuedShot> shots = new ArrayList<>();
        private boolean sneaking;
    }
}
