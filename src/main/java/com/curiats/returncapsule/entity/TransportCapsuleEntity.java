package com.curiats.returncapsule.entity;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.util.BoundLoadout;
import com.curiats.returncapsule.entity.fall.CapsuleKind;
import com.curiats.returncapsule.entity.fall.CapsuleLanding;
import com.curiats.returncapsule.entity.fall.CapsuleParticles;
import com.curiats.returncapsule.entity.fall.ShakeBurst;
import com.curiats.returncapsule.menu.CapsuleMenu;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.progress.TierResolver;
import com.curiats.returncapsule.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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

@SuppressWarnings({"this-escape",
        "deprecation"})
public class TransportCapsuleEntity extends AbstractCapsuleEntity implements MenuProvider {
    private static final double[][] SEAT_OFFSETS_SINGLE = {{0.0D, 0.0D}};
    private static final double[][] SEAT_OFFSETS_PAIR = {{-0.5D, 0.0D}, {0.5D, 0.0D}};
    private static final double[][] SEAT_OFFSETS_TRIO = {{-0.55D, 0.35D}, {0.55D, 0.35D}, {0.0D, -0.55D}};
    /**
     * Derived from the model: the hull floor sits at model y=5, the model is scaled 2x and 1/16 per unit,
     * so the floor is 5 * 2 / 16 = 0.625 blocks above the entity origin.
     */
    private static final double PASSENGER_SEAT_HEIGHT = 0.65D;

    private static final String TAG_HUMANOID = "Humanoid";

    /** Only the summon path carries a humanoid; riding back home lands the same capsule without one. */
    private boolean withHumanoid;

    private double entryStartX;

    private double entryStartZ;

    private final java.util.Set<net.minecraft.world.level.ChunkPos> windowTickets = new java.util.HashSet<>();

    public TransportCapsuleEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static boolean spawnManned(ServerLevel server, BlockPos landing, ServerPlayer owner) {
        TransportCapsuleEntity entity = new TransportCapsuleEntity(ModEntities.TRANSPORT_CAPSULE.get(), server);
        entity.withHumanoid = true;
        entity.beginFall(landing, owner, Config.mannedSpawnHeight());
        entity.startLateralApproach(landing);
        return server.addFreshEntity(entity);
    }

    public static boolean spawn(ServerLevel server, BlockPos markerPos, ServerPlayer owner) {
        SummonTier tier = TierResolver.resolve(server, markerPos, owner);
        TransportCapsuleEntity entity = new TransportCapsuleEntity(ModEntities.TRANSPORT_CAPSULE.get(), server);
        entity.setTier(tier);
        entity.beginFall(markerPos, owner, Config.mannedSpawnHeight());
        entity.startLateralApproach(markerPos);
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

    /** True for the summon path: it carries the humanoid and the fall flare. */
    public boolean isMannedPayload() {
        return this.withHumanoid;
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
        double remaining = this.getY() - (this.anchor == null ? this.getY() : this.anchor.getY());
        if (remaining > 3000.0D) {
            return -Math.min(Config.mannedCruiseSpeed(), 8.0D + remaining * 0.05D);
        }
        return -Config.TERMINAL_VELOCITY.get();
    }

    @Override
    protected boolean phaseThroughFall() {
        return this.getY() - (this.anchor == null ? this.getY() : this.anchor.getY()) > 3000.0D;
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
        double seatX = this.getX() + dx;
        double seatY = this.getY() + PASSENGER_SEAT_HEIGHT;
        double seatZ = this.getZ() + dz;
        moveFunction.accept(passenger, seatX, seatY, seatZ);
        // Both entities render from their own old position; align the rider with the hull's previous position so the
        // two interpolate over the same segment instead of drifting apart at terminal velocity.
        passenger.xo = this.xo + dx;
        passenger.yo = this.yo + PASSENGER_SEAT_HEIGHT;
        passenger.zo = this.zo + dz;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.hasPassenger(player)) {
            player.openMenu(this);
            return InteractionResult.CONSUME;
        }
        return super.interact(player, hand);
    }

    /** Every designator path lands the manned capsule; the supply capsule is the only SUPPLY user. */
    @Override
    public CapsuleKind capsuleKind() {
        return CapsuleKind.MANNED;
    }

    @Override
    protected void impactEffects(ServerLevel server, BlockPos landing) {
        super.impactEffects(server, landing);
        if (!this.getPassengers().isEmpty()) {
            ShakeBurst.forPassengers(this, 25, 3);
        }
    }

    @Override
    protected void beforeShock(ServerLevel server, BlockPos landing) {
        // The original implementation ejected the passengers before the shock, not after it.
        if (!this.getPassengers().isEmpty()) {
            this.ejectPassengers(server);
        }
    }

    @Override
    protected void afterShock(ServerLevel server, BlockPos landing) {
        if (this.withHumanoid) {
            return;
        }
        double x = landing.getX() + 0.5D;
        double y = landing.getY() + 1.0D;
        double z = landing.getZ() + 0.5D;
        CapsuleParticles.broadcast(server, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 4, 2.0D, 1.0D, 2.0D, 0.0D);
        CapsuleParticles.broadcast(server, ParticleTypes.EXPLOSION, x, y, z, 30, 4.5D, 1.0D, 4.5D, 0.0D);
    }

    @Override
    protected void placeCargo(ServerLevel server, BlockPos landing) {
        boolean placed = CapsuleLanding.land(server, landing, this.landingFacing, capsuleKind(),
                this.inventory, this.tier);
        if (!placed) {
            // Nothing to open: spill the cargo instead of deleting it, and leave the kit unclaimed for the next capsule.
            net.minecraft.world.Containers.dropContents(server, landing, this.inventory);
            this.inventory.clearContent();
            return;
        }
        // The kit is issued on first open, so losing the capsule before that does not burn the player's one grant.
        if (this.ownerUuid != null && server.getBlockEntity(landing)
                instanceof com.curiats.returncapsule.block.CapsuleBlockEntity capsule) {
            capsule.setKitOwner(this.ownerUuid);
        }
        if (this.withHumanoid) {
            this.spawnHumanoid(server, landing);
        }
    }

    /** Spawns the owned humanoid beside the placed block so it does not end up inside the capsule collision. */
    private void spawnHumanoid(ServerLevel server, BlockPos landing) {
        NeutralHumanoid humanoid = ModEntities.BASE_HUMANOID.get().create(server);
        if (humanoid == null) {
            return;
        }
        BlockPos spot = this.findFreeSpot(server, landing);
        humanoid.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, this.getYRot(), 0.0F);
        humanoid.setOwnerUUID(this.ownerUuid);
        humanoid.setPersistenceRequired();
        server.addFreshEntity(humanoid);
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
        return server.getBlockState(pos).getCollisionShape(server, pos).isEmpty()
                && server.getFluidState(pos).isEmpty();
    }

    /** Outside the 5x5 shell: the capsule itself is three blocks tall and fully solid. */
    private BlockPos findFreeSpot(ServerLevel server, BlockPos landing) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int radius = 3; radius <= 5; radius++) {
            for (int[] dir : directions) {
                BlockPos candidate = landing.offset(dir[0] * radius, 0, dir[1] * radius);
                if (isFree(server, candidate) && isFree(server, candidate.above())) {
                    return candidate;
                }
            }
        }
        for (int dy = 3; dy <= 6; dy++) {
            BlockPos above = landing.above(dy);
            if (isFree(server, above) && isFree(server, above.above())) {
                return above;
            }
        }
        return landing.above(6);
    }

    private void startLateralApproach(BlockPos target) {
        int distance = Config.mannedHorizontalDistance();
        if (distance <= 0) {
            return;
        }
        this.entryStartX = target.getX() + 0.5D + distance;
        this.entryStartZ = target.getZ() + 0.5D;
        this.setPos(this.entryStartX, this.getY(), this.entryStartZ);
        if (this.level() instanceof ServerLevel server) {
            this.refreshWindow(server);
        }
    }

    @Override
    protected void onFallTick(ServerLevel server) {
        if (this.anchor == null || Config.mannedHorizontalDistance() <= 0) {
            return;
        }
        double remaining = this.getY() - this.anchor.getY();
        if (this.fallAge % 5 == 0) {
            // The capsule sits 2048 blocks away from the anchor, so its own chunk needs a ticket for the whole fall.
            this.refreshWindow(server);
        }
        double start = Config.mannedHorizontalStart();
        if (remaining > start || remaining <= 0.0D) {
            return;
        }
        double progress = net.minecraft.util.Mth.clamp(1.0D - remaining / start, 0.0D, 1.0D);
        double x = net.minecraft.util.Mth.lerp(progress, this.entryStartX, this.anchor.getX() + 0.5D);
        double z = net.minecraft.util.Mth.lerp(progress, this.entryStartZ, this.anchor.getZ() + 0.5D);
        this.setPos(x, this.getY(), z);
    }

    /** Only four chunks at a time: current chunk plus three ahead along the approach. */
    private void refreshWindow(ServerLevel server) {
        net.minecraft.world.level.ChunkPos current =
                new net.minecraft.world.level.ChunkPos(this.blockPosition());
        java.util.Set<net.minecraft.world.level.ChunkPos> wanted = new java.util.HashSet<>(4);
        wanted.add(current);
        double dx = (this.anchor == null ? 0.0D : this.anchor.getX() + 0.5D - this.getX());
        double dz = (this.anchor == null ? 0.0D : this.anchor.getZ() + 0.5D - this.getZ());
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0.01D) {
            dx /= length;
            dz /= length;
            for (int step = 1; step < 4; step++) {
                wanted.add(new net.minecraft.world.level.ChunkPos(
                        new BlockPos((int) Math.floor(this.getX() + dx * 16.0D * step),
                                (int) Math.floor(this.getY()),
                                (int) Math.floor(this.getZ() + dz * 16.0D * step))));
            }
        }
        this.windowTickets.removeIf(chunk -> {
            if (wanted.contains(chunk)) {
                return false;
            }
            server.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.PORTAL,
                    chunk, 2, BlockPos.of(chunk.toLong()));
            return true;
        });
        for (net.minecraft.world.level.ChunkPos chunk : wanted) {
            if (this.windowTickets.add(chunk)) {
                server.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL,
                        chunk, 2, BlockPos.of(chunk.toLong()));
                server.getChunk(chunk.x, chunk.z);
            }
        }
    }

    private void releaseWindow() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        for (net.minecraft.world.level.ChunkPos chunk : this.windowTickets) {
            server.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.PORTAL,
                    chunk, 2, BlockPos.of(chunk.toLong()));
        }
        this.windowTickets.clear();
    }

    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        this.releaseWindow();
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_HUMANOID, this.withHumanoid);
        tag.putDouble("EntryStartX", this.entryStartX);
        tag.putDouble("EntryStartZ", this.entryStartZ);
        if (this.anchor != null) {
            tag.putLong(TAG_LEGACY_MARKER, this.anchor.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.withHumanoid = tag.getBoolean(TAG_HUMANOID);
        this.entryStartX = tag.getDouble("EntryStartX");
        this.entryStartZ = tag.getDouble("EntryStartZ");
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
