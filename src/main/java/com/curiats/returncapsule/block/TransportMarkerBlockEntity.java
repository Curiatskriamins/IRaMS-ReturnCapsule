package com.curiats.returncapsule.block;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.registry.ModBlocks;
import com.curiats.returncapsule.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class TransportMarkerBlockEntity extends BlockEntity {
    private static final int TICK_INTERVAL = 20;

    @Nullable
    private UUID owner;
    @Nullable
    private UUID token;
    private BlockPos platformCenter = BlockPos.ZERO;
    private long missingSince;
    private final net.minecraft.world.phys.AABB renderBox;

    public TransportMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(com.curiats.returncapsule.registry.ModBlockEntities.TRANSPORT_MARKER.get(), pos, state);
        this.renderBox = new net.minecraft.world.phys.AABB(pos).inflate(16.0D, 256.0D, 16.0D);
    }

    public void configure(UUID owner, UUID token, BlockPos platformCenter) {
        this.owner = owner;
        this.token = token;
        this.platformCenter = platformCenter.immutable();
        this.setChanged();
    }

    @Nullable
    public UUID getToken() {
        return this.token;
    }

    public BlockPos getPlatformCenter() {
        return this.platformCenter;
    }

    public boolean platformValid() {
        if (!(this.level instanceof ServerLevel server) || this.platformCenter == null) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!server.getBlockState(this.platformCenter.offset(dx, 0, dz)).is(Blocks.OBSIDIAN)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    public void serverTick(ServerLevel server) {
        if (server.getGameTime() % TICK_INTERVAL != 0L) {
            return;
        }
        if (!this.platformValid()) {
            this.removeMarker(server);
            return;
        }
        if (this.owner == null || this.token == null) {
            return;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(this.owner);
        if (player == null) {
            return;
        }
        if (hasToken(player, this.token)) {
            this.missingSince = 0L;
        } else if (this.missingSince == 0L) {
            this.missingSince = server.getGameTime();
        } else if (server.getGameTime() - this.missingSince > Config.MARKER_GRACE_TICKS.get()) {
            this.removeMarker(server);
        }
    }

    private static boolean hasToken(ServerPlayer player, UUID token) {
        if (containsToken(player.getInventory(), token)) {
            return true;
        }
        return containsToken(player.getEnderChestInventory(), token);
    }

    private static boolean containsToken(Container container, UUID token) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(ModItems.TRANSPORT_DESIGNATOR.get())
                    && stack.hasTag() && token.equals(stack.getTag().getUUID("MarkerToken"))) {
                return true;
            }
        }
        return false;
    }

    private void removeMarker(ServerLevel server) {
        server.removeBlock(this.worldPosition, false);
    }

    public void forceChunks(ServerLevel server) {
        if (this.platformCenter == null) {
            return;
        }
        int radius = Config.MARKER_CHUNK_RADIUS.get();
        int cx = this.platformCenter.getX() >> 4;
        int cz = this.platformCenter.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                server.setChunkForced(cx + dx, cz + dz, true);
            }
        }
    }

    public void releaseChunks() {
        if (!(this.level instanceof ServerLevel server) || this.platformCenter == null) {
            return;
        }
        int radius = Config.MARKER_CHUNK_RADIUS.get();
        int cx = this.platformCenter.getX() >> 4;
        int cz = this.platformCenter.getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                server.setChunkForced(cx + dx, cz + dz, false);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        if (this.token != null) {
            tag.putUUID("Token", this.token);
        }
        tag.putLong("Platform", this.platformCenter.asLong());
        tag.putLong("MissingSince", this.missingSince);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.token = tag.hasUUID("Token") ? tag.getUUID("Token") : null;
        this.platformCenter = BlockPos.of(tag.getLong("Platform"));
        this.missingSince = tag.getLong("MissingSince");
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return this.renderBox;
    }
}
