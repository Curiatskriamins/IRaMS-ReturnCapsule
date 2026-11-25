package com.curiats.returncapsule.block;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.fall.CapsuleKind;
import com.curiats.returncapsule.menu.CapsuleMenu;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.registry.ModBlockEntities;
import com.curiats.returncapsule.registry.ModBlocks;
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

@SuppressWarnings({"this-escape"})
public class CapsuleBlockEntity extends RandomizableContainerBlockEntity {
    public enum AnimState {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING
    }

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_KIT_OWNER = "KitOwner";
    private static final String TAG_PRESSURE = "PressureEqualized";
    private static final String TAG_TIER = "Tier";
    private static final int SHELL_REPAIR_INTERVAL = 40;

    private final boolean manned;
    private final int slots;
    private final CapsuleAnimator animator;
    private final CapsuleLoot loot;
    private final AABB renderBox;

    private NonNullList<ItemStack> items;
    @Nullable
    private UUID owner;

    /** Not null while the first-open kit has not been issued yet. */
    @javax.annotation.Nullable
    private UUID kitOwner;
    private SummonTier tier = SummonTier.BASIC;
    private int repairCooldown = SHELL_REPAIR_INTERVAL;

    public CapsuleBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.CAPSULE.get(), pos, state, false);
    }

    protected CapsuleBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                 BlockPos pos, BlockState state, boolean manned) {
        super(type, pos, state);
        this.manned = manned;
        this.slots = manned ? 6 : Config.containerSlots();
        this.items = NonNullList.withSize(this.slots, ItemStack.EMPTY);
        this.animator = new CapsuleAnimator(this);
        this.loot = new CapsuleLoot(this);
        // the manned model is 5 wide, 3 tall, so the tiny supply render box would cull it.
        this.renderBox = manned
                ? new AABB(pos.offset(-2, 0, -2), pos.offset(3, 4, 3))
                : new AABB(pos.offset(-1, 0, -1), pos.offset(2, 3, 2));
    }

    public AnimState getAnimState() {
        return this.animator.state();
    }

    public float getLidProgress(float partialTick) {
        return this.animator.lidProgress(partialTick);
    }

    private boolean pressureEqualized;

    /** The opening hiss is escaping air: it happens once, when the capsule first equalises, not on every lid cycle. */
    public boolean claimPressureSound() {
        if (this.pressureEqualized) {
            return false;
        }
        this.pressureEqualized = true;
        this.setChanged();
        return true;
    }

    public void startOpening() {
        this.animator.open();
    }

    public void startClosing() {
        this.animator.close();
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
        if (--this.repairCooldown <= 0) {
            this.repairCooldown = SHELL_REPAIR_INTERVAL;
            // repair the footprint this block actually has instead of always the 3x3 supply one.
            CapsuleKind kind = this.manned() ? CapsuleKind.MANNED : CapsuleKind.SUPPLY;
            if (!CapsuleStructure.isIntact(server, this.worldPosition, kind)) {
                CapsuleStructure.placeParts(server, this.worldPosition, kind);
            }
        }
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
        com.curiats.returncapsule.ReturnCapsuleMod.LOGGER.info(
                "[capsule] name query manned={} type={} tier={}", this.manned(), this.getType(), this.tier);
        if (this.manned()) {
            return Component.translatable("block.returncapsule.manned_capsule");
        }
        return Component.translatable(this.tier.containerKey());
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    public boolean manned() {
        return this.manned;
    }

    public boolean isEmptyContainer() {
        for (int i = 0; i < this.getContainerSize(); i++) {
            if (!this.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        com.curiats.returncapsule.ReturnCapsuleMod.LOGGER.info(
                "[capsule] menu for {} manned={} slots={} type={}", this.getBlockState().getBlock(),
                this.manned(), this.slots, this.getType());
        return new CapsuleMenu(id, inventory, this, this.manned());
    }

    @Nullable
    public UUID getOwner() {
        return this.owner;
    }

    @Nullable
    public UUID kitOwner() {
        return this.kitOwner;
    }

    public void setKitOwner(@Nullable UUID owner) {
        this.kitOwner = owner;
        this.setChanged();
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
        this.kitOwner = tag.hasUUID(TAG_KIT_OWNER) ? tag.getUUID(TAG_KIT_OWNER) : null;
        this.pressureEqualized = tag.getBoolean(TAG_PRESSURE);
        this.tier = SummonTier.byName(tag.getString(TAG_TIER));
        this.loot.load(tag, hasLootTable);
        this.animator.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items);
        }
        if (this.kitOwner != null) {
            tag.putUUID(TAG_KIT_OWNER, this.kitOwner);
        }
        tag.putBoolean(TAG_PRESSURE, this.pressureEqualized);
        if (this.owner != null) {
            tag.putUUID(TAG_OWNER, this.owner);
        }
        tag.putString(TAG_TIER, this.tier.id());
        this.loot.save(tag);
        this.animator.save(tag);
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
