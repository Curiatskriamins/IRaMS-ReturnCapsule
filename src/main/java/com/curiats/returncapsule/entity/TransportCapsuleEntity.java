package com.curiats.returncapsule.entity;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.compat.ShockManager;
import com.curiats.returncapsule.compat.TransportLoot;
import com.curiats.returncapsule.entity.fall.CapsuleParticles;
import com.curiats.returncapsule.entity.fall.CapsulePlacer;
import com.curiats.returncapsule.entity.fall.ShakeBurst;
import com.curiats.returncapsule.menu.CapsuleMenu;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.progress.TierResolver;
import com.curiats.returncapsule.registry.ModEntities;
import com.curiats.returncapsule.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TransportCapsuleEntity extends FallingCapsuleEntity implements MenuProvider {
    private static final double[][] SEAT_OFFSETS_SINGLE = {{0.0D, 0.0D}};
    private static final double[][] SEAT_OFFSETS_PAIR = {{-0.5D, 0.0D}, {0.5D, 0.0D}};
    private static final double[][] SEAT_OFFSETS_TRIO = {{-0.55D, 0.35D}, {0.55D, 0.35D}, {0.0D, -0.55D}};
    private static final double PASSENGER_SEAT_HEIGHT = 0.1D;

    private final CapsuleInventory inventory;

    public TransportCapsuleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.inventory = new CapsuleInventory(Config.containerSlots(),
                player -> this.hasPassenger(player) || player.distanceToSqr(this) < 64.0D);
    }

    public static boolean spawn(ServerLevel server, BlockPos markerPos, ServerPlayer owner) {
        SummonTier tier = TierResolver.resolve(server, markerPos, owner);
        TransportCapsuleEntity entity = new TransportCapsuleEntity(ModEntities.TRANSPORT_CAPSULE.get(), server);
        entity.setTier(tier);
        entity.beginFall(markerPos, owner, Config.spawnHeight());
        TransportLoot.fill(entity.inventory, server, server.random, tier);
        if (!server.addFreshEntity(entity)) {
            return false;
        }
        if (!entity.addPassenger(owner)) {
            entity.discard();
            return false;
        }
        return true;
    }

    @Nullable
    public static TransportCapsuleEntity find(Level level, BlockPos markerPos) {
        if (level.isClientSide) {
            return null;
        }
        AABB area = new AABB(markerPos).inflate(2.0D, 2000.0D, 2.0D);
        for (TransportCapsuleEntity candidate : level.getEntitiesOfClass(TransportCapsuleEntity.class, area)) {
            if (!candidate.impacted && markerPos.equals(candidate.anchor)) {
                return candidate;
            }
        }
        return null;
    }

    public CapsuleInventory inventory() {
        return this.inventory;
    }

    public boolean addPassenger(ServerPlayer player) {
        if (this.impacted || this.getPassengers().size() >= Config.TRANSPORT_MAX_PASSENGERS.get()) {
            return false;
        }
        return player.startRiding(this, true);
    }

    public boolean isImpacted() {
        return this.impacted;
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < Config.TRANSPORT_MAX_PASSENGERS.get();
    }

    @Override
    protected double fallSpeed(double currentVelocity) {
        return -Config.TERMINAL_VELOCITY.get();
    }

    @Override
    protected void applyFallShake(ServerLevel server) {
        if (!Config.SCREEN_SHAKE.get() || this.anchor == null) {
            return;
        }
        double start = Config.SHAKE_START_DISTANCE.get();
        if (this.position().distanceToSqr(Vec3.atCenterOf(this.anchor)) > start * start) {
            return;
        }
        int amplifier = Math.min(3, Math.max(0, this.fallAge / 100));
        ShakeBurst.forPassengers(this, 10, amplifier);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        int count = Math.max(1, this.getPassengers().size());
        int index = Math.max(0, this.getPassengers().indexOf(passenger));
        double[][] offsets = count == 1 ? SEAT_OFFSETS_SINGLE
                : count == 2 ? SEAT_OFFSETS_PAIR
                : SEAT_OFFSETS_TRIO;
        double[] offset = offsets[Math.min(index, offsets.length - 1)];
        double yaw = Math.toRadians(this.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double dx = offset[0] * cos - offset[1] * sin;
        double dz = offset[0] * sin + offset[1] * cos;
        moveFunction.accept(passenger, this.getX() + dx,
                this.getY() + PASSENGER_SEAT_HEIGHT, this.getZ() + dz);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.hasPassenger(player)) {
            player.openMenu(this);
            return InteractionResult.CONSUME;
        }
        return super.interact(player, hand);
    }

    @Override
    protected void onLanded(ServerLevel server) {
        if (this.impacted || this.anchor == null) {
            return;
        }
        this.impacted = true;
        BlockPos landing = this.anchor;
        double x = landing.getX() + 0.5D;
        double y = landing.getY() + 1.0D;
        double z = landing.getZ() + 0.5D;

        server.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                Config.IMPACT_SOUND_VOLUME.get().floatValue(),
                Config.IMPACT_SOUND_PITCH.get().floatValue());
        server.playSound(null, x, y, z, ModSounds.CAPSULE_IMPACT.get(), SoundSource.BLOCKS,
                Config.IMPACT_THUD_VOLUME.get().floatValue(),
                Config.IMPACT_THUD_PITCH.get().floatValue());
        ShakeBurst.forPassengers(this, 25, 3);

        Set<UUID> excluded = new HashSet<>();
        for (Entity passenger : this.getPassengers()) {
            excluded.add(passenger.getUUID());
        }
        this.ejectPassengers(server);
        ShockManager.impact(server, new Vec3(x, y, z), Config.IMPACT_HALF_SIZE.get(), this, excluded);

        CapsuleParticles.broadcast(server, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 4, 2.0D, 1.0D, 2.0D, 0.0D);
        CapsuleParticles.broadcast(server, ParticleTypes.EXPLOSION, x, y, z, 30, 4.5D, 1.0D, 4.5D, 0.0D);

        CapsulePlacer.withItems(server, landing, this.landingFacing, this.inventory, this.tier);
        CapsuleFire.spread(server, new Vec3(x, y, z),
                Config.LANDING_FIRE_RADIUS.get(), Config.LANDING_FIRE_CHANCE.get());
        this.discard();
    }

    private void ejectPassengers(ServerLevel server) {
        BlockPos exit = this.findSafeExit(server);
        for (Entity passenger : List.copyOf(this.getPassengers())) {
            passenger.stopRiding();
            if (passenger instanceof ServerPlayer player) {
                double x = exit.getX() + 0.5D;
                double y = exit.getY();
                double z = exit.getZ() + 0.5D;
                player.teleportTo(x, y, z);
                player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
                player.setNoGravity(false);
                player.setPose(Pose.STANDING);
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private BlockPos findSafeExit(ServerLevel server) {
        int[] radii = {2, 3, 4};
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int radius : radii) {
            for (int[] dir : directions) {
                BlockPos candidate = this.anchor.offset(dir[0] * radius, 0, dir[1] * radius);
                if (isFree(server, candidate) && isFree(server, candidate.above())) {
                    return candidate;
                }
            }
        }
        return this.anchor.above(2);
    }

    private static boolean isFree(ServerLevel server, BlockPos pos) {
        return server.getBlockState(pos).getCollisionShape(server, pos).isEmpty();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.anchor != null) {
            tag.putLong(TAG_LEGACY_MARKER, this.anchor.asLong());
        }
        this.inventory.save(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.inventory.load(tag);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(this.tier.transportKey());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CapsuleMenu(id, inventory, this.inventory);
    }
}
