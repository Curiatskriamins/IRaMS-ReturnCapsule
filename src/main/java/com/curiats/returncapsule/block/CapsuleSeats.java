package com.curiats.returncapsule.block;

import com.curiats.returncapsule.registry.ModSounds;
import com.curiats.returncapsule.util.HiddenPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class CapsuleSeats {
    static final int CAPACITY = 3;

    private static final double[][] SEAT_OFFSETS = {
            {-0.55D, 0.35D},
            {0.55D, 0.35D},
            {0.0D, -0.55D}
    };
    private static final double LOCK_DISTANCE_SQR = 0.04D;
    private static final double SEAT_HEIGHT = 0.1D;
    private static final int EXIT_DISTANCE = 2;

    private final CapsuleBlockEntity owner;
    private final UUID[] occupants = new UUID[CAPACITY];

    CapsuleSeats(CapsuleBlockEntity owner) {
        this.owner = owner;
    }

    boolean isEmpty() {
        for (UUID id : this.occupants) {
            if (id != null) {
                return false;
            }
        }
        return true;
    }

    boolean add(ServerPlayer player) {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide
                || !this.owner.isOpenOrClosing()) {
            return false;
        }
        if (HiddenPlayers.isHidden(player)) {
            return false;
        }
        for (int index = 0; index < CAPACITY; index++) {
            if (this.occupants[index] == null) {
                this.occupants[index] = player.getUUID();
                HiddenPlayers.put(player.getUUID(), this.owner);
                this.lock(player, index);
                if (this.owner.isOpen()) {
                    this.owner.startClosing();
                }
                this.owner.syncToClients();
                return true;
            }
        }
        return false;
    }

    void tick(ServerLevel server) {
        for (int index = 0; index < CAPACITY; index++) {
            UUID id = this.occupants[index];
            if (id == null) {
                continue;
            }
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
            if (player == null || player.level() != server) {
                this.release(id);
                continue;
            }
            HiddenPlayers.put(id, this.owner);
            this.lock(player, index);
            if (!player.isShiftKeyDown()) {
                continue;
            }
            if (this.owner.isOpen()) {
                this.eject(player, index);
            } else if (this.owner.isClosed()) {
                this.owner.startOpening();
                this.owner.getLevel().playSound(null, this.owner.getBlockPos(),
                        ModSounds.CAPSULE_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                com.curiats.returncapsule.ReturnCapsuleMod.LOGGER.info(
                        "[sound] capsule_open seat at {}", this.owner.getBlockPos());
            }
        }
    }

    void ejectAll(ServerLevel server) {
        if (this.isEmpty()) {
            return;
        }
        BlockPos exit = this.exitPos();
        for (int index = 0; index < CAPACITY; index++) {
            UUID id = this.occupants[index];
            if (id == null) {
                continue;
            }
            this.occupants[index] = null;
            HiddenPlayers.remove(id);
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                this.placeAtExit(player, exit);
            }
        }
    }

    private void eject(ServerPlayer player, int index) {
        this.occupants[index] = null;
        HiddenPlayers.remove(player.getUUID());
        this.placeAtExit(player, this.exitPos());
        this.owner.syncToClients();
    }

    private void release(UUID id) {
        for (int index = 0; index < CAPACITY; index++) {
            if (id.equals(this.occupants[index])) {
                this.occupants[index] = null;
            }
        }
        HiddenPlayers.remove(id);
    }

    private void lock(ServerPlayer player, int index) {
        Vec3 seat = this.seat(index);
        if (player.distanceToSqr(seat) > LOCK_DISTANCE_SQR) {
            player.teleportTo(seat.x, seat.y, seat.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
        player.setPose(Pose.SITTING);
        player.setInvisible(true);
    }

    private void placeAtExit(ServerPlayer player, BlockPos exit) {
        player.teleportTo(exit.getX() + 0.5D, exit.getY(), exit.getZ() + 0.5D);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(false);
        player.setPose(Pose.STANDING);
        player.setInvisible(false);
    }

    private BlockPos exitPos() {
        Direction facing = this.owner.getBlockState().getValue(CapsuleBlock.FACING);
        return this.owner.getBlockPos().relative(facing, EXIT_DISTANCE);
    }

    private Vec3 seat(int index) {
        double[] offset = SEAT_OFFSETS[Math.max(0, Math.min(index, SEAT_OFFSETS.length - 1))];
        BlockPos pos = this.owner.getBlockPos();
        return new Vec3(pos.getX() + 0.5D + offset[0], pos.getY() + SEAT_HEIGHT, pos.getZ() + 0.5D + offset[1]);
    }

    void onLoad() {
        for (UUID id : this.occupants) {
            if (id != null) {
                HiddenPlayers.put(id, this.owner);
            }
        }
    }

    void save(CompoundTag tag) {
        for (int index = 0; index < CAPACITY; index++) {
            if (this.occupants[index] != null) {
                tag.putUUID("Hidden" + index, this.occupants[index]);
            }
        }
    }

    void load(CompoundTag tag) {
        for (int index = 0; index < CAPACITY; index++) {
            this.occupants[index] = tag.hasUUID("Hidden" + index) ? tag.getUUID("Hidden" + index) : null;
        }
    }
}
