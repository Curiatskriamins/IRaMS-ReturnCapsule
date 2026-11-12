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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

 
public class KillMarkerItem extends Item {
    private static final int USE_DURATION = 72000;
    private static final Map<UUID, BoomEntity> HELD = new HashMap<>();

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
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            server.playNotifySound(ModSounds.MISSILE_LOCK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            ReturnCapsuleMod.LOGGER.info("[sound] kill_marker use -> missile_lock");
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(context.getHand());
        if (!context.getLevel().isClientSide && player instanceof ServerPlayer server) {
            server.playNotifySound(ModSounds.MISSILE_LOCK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            ReturnCapsuleMod.LOGGER.info("[sound] kill_marker useOn -> missile_lock");
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(living instanceof ServerPlayer player)) {
            return;
        }
        int elapsed = this.getUseDuration(stack) - remainingUseDuration;
        if (elapsed != 0 || HELD.containsKey(player.getUUID())) {
            return;
        }
        Shot shot = resolveShot(player, level);
        if (shot == null) {
            return;
        }
        if (shot.entity() != null
                && level instanceof ServerLevel server
                && server.getEntity(shot.entity()) instanceof TamableAnimal pet
                && pet.isTame()) {
            PetLaunch.launch(pet);
            return;
        }
        Payment payment = pay(player);
        if (payment == null) {
            return;
        }
        double angle = SuperBigCup.nextIncidence();
        BoomEntity bomb = spawnBomb(level, player, shot.point(), shot.entity(), angle, null);
        if (bomb == null) {
            refund(player, payment);
            return;
        }
        HELD.put(player.getUUID(), bomb);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (level.isClientSide || !(living instanceof ServerPlayer player)) {
            return;
        }
        BoomEntity bomb = HELD.remove(player.getUUID());
        if (bomb == null) {
            return;
        }
        if (player.isDeadOrDying()) {
            bomb.discard();
            return;
        }
        bomb.release();
    }

    @Nullable
    private static BoomEntity spawnBomb(Level level, ServerPlayer player, Vec3 point,
                                        @Nullable UUID entity, double angle,
                                        @Nullable Set<Double> reservedAngles) {
        if (!(level instanceof ServerLevel server)) {
            return null;
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
            return null;
        }
        if (!server.addFreshEntity(bomb)) {
            return null;
        }
        if (reservedAngles != null) {
            reservedAngles.add(controller.incidenceDegrees());
        }
        bomb.hold();
        return bomb;
    }

     
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

     
    @Nullable
    private static Shot resolveShot(ServerPlayer player, Level level) {
        
        
        int simulation = Math.max(1, player.getServer().getPlayerList().getSimulationDistance());
        int margin = Config.BOOM_SPAWN_MARGIN_CHUNKS.get();
        double simulationLimit = Math.max(8.0D, (simulation - margin) * 16.0D - 16.0D);
        double range = Math.min(Config.KILL_MARKER_RANGE.get(), simulationLimit);

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
        AABB search = new AABB(eye, end).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, end, search,
                entity -> entity != player && entity instanceof LivingEntity && entity.isAlive()
                        && !entity.isSpectator() && entity.isPickable());
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (entityHit != null && (blockHit.getType() == HitResult.Type.MISS
                || entityHit.getLocation().distanceToSqr(eye)
                <= blockHit.getLocation().distanceToSqr(eye))) {
            Entity entity = entityHit.getEntity();
            return new Shot(entity.getBoundingBox().getCenter(), entity.getUUID());
        }
        Shot crosshair = findCrosshairLiving(player, level, eye, player.getViewVector(1.0F), range);
        if (crosshair != null) {
            return crosshair;
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            Direction face = blockHit.getDirection();
            Vec3 point = blockHit.getLocation().add(
                    face.getStepX() * 0.05D, face.getStepY() * 0.05D, face.getStepZ() * 0.05D);
            return new Shot(point, null);
        }
        return null;
    }

    @Nullable
    private static Shot findCrosshairLiving(ServerPlayer player, Level level, Vec3 eye, Vec3 look,
                                             double range) {
        final double maxCone = Math.toRadians(20.0D);
        AABB area = new AABB(eye, eye.add(look.scale(range))).inflate(2.0D);
        LivingEntity best = null;
        double bestAngle = maxCone;
        Vec3 rayEnd = eye.add(look.scale(range));
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable())) {
            AABB box = entity.getBoundingBox();
            java.util.Optional<Vec3> clip = box.clip(eye, rayEnd);
            if (clip.isEmpty()) {
                continue;
            }
            Vec3 point = clip.get();
            Vec3 delta = point.subtract(eye);
            double distance = delta.length();
            if (distance < 1.0E-4D || distance > range) {
                continue;
            }
            double dot = Mth.clamp(delta.scale(1.0D / distance).dot(look), -1.0D, 1.0D);
            double angle = Math.acos(dot);
            if (angle >= bestAngle) {
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(eye, point,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS
                    && hit.getLocation().distanceToSqr(eye) < point.distanceToSqr(eye) - 0.25D) {
                continue;
            }
            best = entity;
            bestAngle = angle;
        }
        return best == null ? null : new Shot(best.getBoundingBox().getCenter(), best.getUUID());
    }

     
    public static void refundVolley(ServerPlayer player) {
        BoomEntity bomb = HELD.remove(player.getUUID());
        if (bomb != null) {
            bomb.discard();
        }
    }

    public static void dropAll() {
        for (BoomEntity bomb : HELD.values()) {
            if (bomb != null) {
                bomb.discard();
            }
        }
        HELD.clear();
    }

    private record Shot(Vec3 point, @Nullable UUID entity) {
    }

    private record Payment(@Nullable Item item, int count) {
        private static final Payment FREE = new Payment(null, 0);
    }
}
