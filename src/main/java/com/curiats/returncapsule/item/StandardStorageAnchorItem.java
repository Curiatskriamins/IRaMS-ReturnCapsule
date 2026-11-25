package com.curiats.returncapsule.item;

import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.util.AnchorStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Stores up to three base humanoids, one anchor at a time.
 *
 * <p>Right click a humanoid to store it, right click the ground to release one back into the world with its blade and a
 * carried gun.</p>
 */
public class StandardStorageAnchorItem extends Item {

    private static final double RELEASE_RANGE = 6.0D;

    private static final String TAG_GUARD = "DisplacementGuard";

    public static boolean guardEnabled(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG_GUARD)) {
            return true;
        }
        return stack.getTag().getBoolean(TAG_GUARD);
    }

    public static void toggleGuard(ItemStack stack) {
        boolean next = !guardEnabled(stack);
        stack.getOrCreateTag().putBoolean(TAG_GUARD, next);
    }

    public StandardStorageAnchorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(guardEnabled(stack)
                ? "tooltip.returncapsule.anchor_guard_on"
                : "tooltip.returncapsule.anchor_guard_off"));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof NeutralHumanoid humanoid) || !(player instanceof ServerPlayer server)) {
            return InteractionResult.PASS;
        }
        if (server.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (AnchorStorage.store(server, stack, humanoid)) {
            server.displayClientMessage(Component.translatable("message.returncapsule.stored",
                    AnchorStorage.count(stack), AnchorStorage.CAPACITY), true);
            return InteractionResult.SUCCESS;
        }
        server.displayClientMessage(Component.translatable("message.returncapsule.storage_full",
                AnchorStorage.CAPACITY), true);
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.pass(held);
        }
        long gameTime = server.level().getGameTime();
        if (AnchorStorage.count(held) <= 0) {
            server.displayClientMessage(Component.translatable("message.returncapsule.storage_empty"), true);
            return InteractionResultHolder.fail(held);
        }
        if (!AnchorStorage.canRelease(held, gameTime)) {
            long seconds = Math.max(1L, (AnchorStorage.readyAt(held) - gameTime) / 20L);
            server.displayClientMessage(Component.translatable("message.returncapsule.release_cooldown", seconds), true);
            return InteractionResultHolder.fail(held);
        }
        HitResult picked = player.pick(RELEASE_RANGE, 0.0F, false);
        if (!(picked instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(held);
        }
        Vec3 position = hit.getLocation().add(0.0D, 0.1D, 0.0D);
        if (AnchorStorage.release(server, held, position, gameTime)) {
            server.displayClientMessage(Component.translatable("message.returncapsule.released",
                    AnchorStorage.count(held)), true);
            return InteractionResultHolder.success(held);
        }
        return InteractionResultHolder.fail(held);
    }
}
