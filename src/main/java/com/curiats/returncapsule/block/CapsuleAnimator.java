package com.curiats.returncapsule.block;

import com.curiats.returncapsule.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

final class CapsuleAnimator {
    private static final String TAG_STATE = "AnimState";
    private static final String TAG_START = "AnimStart";

    private final CapsuleBlockEntity owner;
    private CapsuleBlockEntity.AnimState state = CapsuleBlockEntity.AnimState.CLOSED;
    private long startTick;

    CapsuleAnimator(CapsuleBlockEntity owner) {
        this.owner = owner;
    }

    CapsuleBlockEntity.AnimState state() {
        return this.state;
    }

    boolean isOpenOrClosing() {
        return this.state == CapsuleBlockEntity.AnimState.OPEN
                || this.state == CapsuleBlockEntity.AnimState.CLOSING;
    }

    boolean isOpen() {
        return this.state == CapsuleBlockEntity.AnimState.OPEN;
    }

    boolean isClosed() {
        return this.state == CapsuleBlockEntity.AnimState.CLOSED;
    }

    void open() {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide
                || this.state != CapsuleBlockEntity.AnimState.CLOSED) {
            return;
        }
        this.state = CapsuleBlockEntity.AnimState.OPENING;
        this.startTick = this.owner.getLevel().getGameTime();
        this.sync();
    }

    void close() {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide
                || this.state == CapsuleBlockEntity.AnimState.CLOSED
                || this.state == CapsuleBlockEntity.AnimState.CLOSING) {
            return;
        }
        this.state = CapsuleBlockEntity.AnimState.CLOSING;
        this.startTick = this.owner.getLevel().getGameTime();
        this.sync();
    }

    void tick(ServerLevel level) {
        long elapsed = level.getGameTime() - this.startTick;
        if (this.state == CapsuleBlockEntity.AnimState.OPENING
                && elapsed >= Config.CAPSULE_OPEN_TICKS.get()) {
            this.state = CapsuleBlockEntity.AnimState.OPEN;
            this.sync();
        } else if (this.state == CapsuleBlockEntity.AnimState.CLOSING
                && elapsed >= Config.CAPSULE_CLOSE_TICKS.get()) {
            this.state = CapsuleBlockEntity.AnimState.CLOSED;
            this.sync();
        }
    }

    float lidProgress(float partialTick) {
        long elapsed = this.owner.getLevel() == null ? 0L
                : this.owner.getLevel().getGameTime() - this.startTick;
        float smoothElapsed = elapsed + partialTick;
        return switch (this.state) {
            case OPENING -> Mth.clamp(smoothElapsed / (float) Config.LID_OPEN_TICKS.get(), 0.0F, 1.0F);
            case OPEN -> 1.0F;
            case CLOSING -> 1.0F - Mth.clamp(smoothElapsed / (float) Config.LID_CLOSE_TICKS.get(), 0.0F, 1.0F);
            case CLOSED -> 0.0F;
        };
    }

    void sync() {
        this.owner.syncToClients();
    }

    void save(CompoundTag tag) {
        tag.putString(TAG_STATE, this.state.name());
        tag.putLong(TAG_START, this.startTick);
    }

    void load(CompoundTag tag) {
        this.state = readState(tag);
        this.startTick = tag.getLong(TAG_START);
    }

    CompoundTag writeSyncTag() {
        CompoundTag tag = new CompoundTag();
        this.save(tag);
        return tag;
    }

    void readSyncTag(CompoundTag tag) {
        this.load(tag);
    }

    private static CapsuleBlockEntity.AnimState readState(CompoundTag tag) {
        if (!tag.contains(TAG_STATE)) {
            return CapsuleBlockEntity.AnimState.CLOSED;
        }
        try {
            return CapsuleBlockEntity.AnimState.valueOf(tag.getString(TAG_STATE));
        } catch (IllegalArgumentException exception) {
            return CapsuleBlockEntity.AnimState.CLOSED;
        }
    }
}
