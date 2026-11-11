package com.curiats.returncapsule.block;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.menu.CapsuleMenu;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.UUID;

public class CapsuleBlockEntity extends RandomizableContainerBlockEntity {
    public enum AnimState {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING
    }

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_TIER = "Tier";
    private static final int SHELL_REPAIR_INTERVAL = 40;

    private final int slots;
    private final CapsuleAnimator animator;
    private final CapsuleSeats seats;
    private final CapsuleLoot loot;
    private final AABB renderBox;

    private NonNullList<ItemStack> items;
    @Nullable
    private UUID owner;
    private SummonTier tier = SummonTier.BASIC;
    private int repairCooldown = SHELL_REPAIR_INTERVAL;

    public CapsuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAPSULE.get(), pos, state);
        this.slots = Config.containerSlots();
        this.items = NonNullList.withSize(this.slots, ItemStack.EMPTY);
        this.animator = new CapsuleAnimator(this);
        this.seats = new CapsuleSeats(this);
        this.loot = new CapsuleLoot(this);
        this.renderBox = new AABB(pos.offset(-1, 0, -1), pos.offset(2, 3, 2));
    }

    public AnimState getAnimState() {
        return this.animator.state();
    }

    public float getLidProgress() {
        return this.animator.lidProgress(0.0F);
    }

    public float getLidProgress(float partialTick) {
        return this.animator.lidProgress(partialTick);
    }

    public void startOpening() {
        this.animator.open();
    }

    public void startClosing() {
        this.animator.close();
    }

    boolean isOpen() {
        return this.animator.isOpen();
    }

    boolean isClosed() {
        return this.animator.isClosed();
    }

    public boolean isOpenOrClosing() {
        return this.animator.isOpenOrClosing();
    }

    void syncToClients() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void serverTick() {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        this.animator.tick(server);
        if (!this.seats.isEmpty()) {
            this.seats.tick(server);
        }
        if (--this.repairCooldown <= 0) {
            this.repairCooldown = SHELL_REPAIR_INTERVAL;
            if (!CapsuleStructure.isIntact(server, this.worldPosition)) {
                CapsuleStructure.placeParts(server, this.worldPosition);
            }
        }
    }

    public boolean addHidden(ServerPlayer player) {
        return this.seats.add(player);
    }

    public void ejectAll(ServerLevel server) {
        this.seats.ejectAll(server);
    }

    public void markLootGenerated() {
        this.loot.markGenerated();
    }

    public void ensureLootTable() {
        this.loot.ensureAssigned();
    }

     
    public void prepareLoot(Player player) {
        this.loot.prepareForAccess(player);
    }

    void assignLootTable(ResourceLocation table, long seed) {
        this.setLootTable(table, seed);
    }

    void clearLootTable() {
        this.lootTable = null;
    }

    void unpackLootTableFor(Player player) {
        this.unpackLootTable(player);
    }

    @Override
    public void setLootTable(ResourceLocation lootTable, long seed) {
        super.setLootTable(lootTable, seed);
        this.loot.onTableAssigned();
    }

    @Override
    public void startOpen(Player player) {
        if (!player.level().isClientSide && !player.isSpectator()) {
            this.loot.prepareForAccess(player);
        }
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(this.tier.containerKey());
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new CapsuleMenu(id, inventory, this);
    }

    @Nullable
    public UUID getOwner() {
        return this.owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    public SummonTier getTier() {
        return this.tier;
    }

    public void setTier(SummonTier tier) {
        this.tier = tier == null ? SummonTier.BASIC : tier;
        if (this.level != null) {
            this.syncToClients();
        } else {
            this.setChanged();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel) {
            this.seats.onLoad();
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel server) {
            this.seats.ejectAll(server);
        }
        super.setRemoved();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        
        
        int saved = tag.getList("Items", Tag.TAG_COMPOUND).size();
        int size = Math.max(this.slots, saved);
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        boolean hasLootTable = this.tryLoadLootTable(tag);
        if (!hasLootTable) {
            ContainerHelper.loadAllItems(tag, this.items);
        }
        this.owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        this.tier = SummonTier.byName(tag.getString(TAG_TIER));
        this.loot.load(tag, hasLootTable);
        this.animator.load(tag);
        this.seats.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items);
        }
        if (this.owner != null) {
            tag.putUUID(TAG_OWNER, this.owner);
        }
        tag.putString(TAG_TIER, this.tier.id());
        this.loot.save(tag);
        this.animator.save(tag);
        this.seats.save(tag);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return this.renderBox;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = this.animator.writeSyncTag();
        tag.putString(TAG_TIER, this.tier.id());
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            this.tier = SummonTier.byName(tag.getString(TAG_TIER));
            this.animator.readSyncTag(tag);
        }
    }
}
