package com.curiats.returncapsule.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

public final class CapsuleInventory extends SimpleContainer {
    private static final String TAG_ITEMS = "Items";

    private final Predicate<Player> validity;

    public CapsuleInventory(int size, Predicate<Player> validity) {
        super(size);
        this.validity = validity;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.validity.test(player);
    }

    public void load(CompoundTag tag) {
        this.fromTag(tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND));
    }

    public void save(CompoundTag tag) {
        tag.put(TAG_ITEMS, this.createTag());
    }
}
