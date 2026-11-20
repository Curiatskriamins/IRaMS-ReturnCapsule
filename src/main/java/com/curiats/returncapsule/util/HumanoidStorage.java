package com.curiats.returncapsule.util;

import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Temporary placeholder storage for the standard issue blade's right click.
 *
 * <p>Only a count is kept, never per entity data: the stored humanoids have no rememberable state, and health is
 * deliberately not part of the deal - a released humanoid always comes back at full health. The weapons the stored
 * humanoids were holding go into a separate, invisible, player bound store (the same idea as an ender chest), and each
 * release draws one random weapon back out of it.</p>
 */
public final class HumanoidStorage {

    private static final String COUNT = "returncapsule_stored_humanoids";

    private static final String GUNS = "returncapsule_stored_guns";

    private static final String RELEASE_READY = "returncapsule_release_ready";

    public static final int MAX_STORED = 10;

    public static final int RELEASE_COOLDOWN_TICKS = 5 * 60 * 20;

    public static int count(Player player) {
        return player.getPersistentData().getInt(COUNT);
    }

    public static int gunCount(Player player) {
        return player.getPersistentData().getList(GUNS, Tag.TAG_COMPOUND).size();
    }

    public static boolean store(ServerPlayer player, NeutralHumanoid humanoid) {
        CompoundTag data = player.getPersistentData();
        if (data.getInt(COUNT) >= MAX_STORED) {
            return false;
        }
        ListTag guns = data.getList(GUNS, Tag.TAG_COMPOUND);
        takeGun(guns, humanoid.getItemInHand(InteractionHand.MAIN_HAND));
        takeGun(guns, humanoid.getItemInHand(InteractionHand.OFF_HAND));
        data.put(GUNS, guns);
        data.putInt(COUNT, data.getInt(COUNT) + 1);
        // No per entity data is written: the humanoid simply ceases to exist and only its weapon survived above.
        humanoid.discard();
        if (humanoid.level() instanceof ServerLevel level) {
            level.playSound(null, humanoid.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return true;
    }

    private static void takeGun(ListTag guns, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        guns.add(stack.save(new CompoundTag()));
    }

    public static boolean canRelease(ServerPlayer player, long gameTime) {
        return count(player) > 0 && gameTime >= player.getPersistentData().getLong(RELEASE_READY);
    }

    public static long readyAt(Player player) {
        return player.getPersistentData().getLong(RELEASE_READY);
    }

    /**
     * Releases one humanoid at {@code position} and hands it a random weapon from the player's store.
     *
     * @return false when there is nothing stored or the five minute release cooldown is still running
     */
    public static boolean release(ServerPlayer player, Vec3 position, long gameTime) {
        if (!canRelease(player, gameTime) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        NeutralHumanoid humanoid = ModEntities.BASE_HUMANOID.get().create(level);
        if (humanoid == null) {
            return false;
        }
        humanoid.moveTo(position.x, position.y, position.z, level.random.nextFloat() * 360.0F, 0.0F);
        humanoid.setHealth(humanoid.getMaxHealth());
        humanoid.setPersistenceRequired();
        CompoundTag data = player.getPersistentData();
        ListTag guns = data.getList(GUNS, Tag.TAG_COMPOUND);
        if (!guns.isEmpty()) {
            int index = level.random.nextInt(guns.size());
            ItemStack weapon = ItemStack.of(guns.getCompound(index));
            guns.remove(index);
            data.put(GUNS, guns);
            // The slot is rolled again instead of being inherited: a stored gun is just "a gun the player owns", and
            // the released humanoid decides afresh whether to carry it in the main or the off hand.
            InteractionHand slot = level.random.nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            humanoid.setItemInHand(slot, weapon);
        }
        data.putInt(COUNT, Math.max(0, data.getInt(COUNT) - 1));
        data.putLong(RELEASE_READY, gameTime + RELEASE_COOLDOWN_TICKS);
        level.addFreshEntity(humanoid);
        level.playSound(null, BlockPos.containing(position), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.9F);
        return true;
    }

    public static void copy(Player from, Player to) {
        CompoundTag source = from.getPersistentData();
        CompoundTag target = to.getPersistentData();
        target.putInt(COUNT, source.getInt(COUNT));
        target.put(GUNS, source.getList(GUNS, Tag.TAG_COMPOUND).copy());
        target.putLong(RELEASE_READY, source.getLong(RELEASE_READY));
    }

    private HumanoidStorage() {
    }
}
