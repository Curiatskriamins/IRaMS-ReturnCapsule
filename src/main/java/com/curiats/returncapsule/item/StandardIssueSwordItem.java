package com.curiats.returncapsule.item;

import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.registry.ModItems;
import com.curiats.returncapsule.util.HumanoidStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Standard issue blade: the base humanoid's melee weapon and the player's matching sword.
 *
 * <p>The old netherite sword relied on sharpness thirty for its damage. This one carries the same result as flat attack
 * damage instead (thirty one total, with no enchantments at all), keeps the unbreakable tag, and trades the old knockback
 * for a slightly faster swing.</p>
 *
 * <p>Right clicking a base humanoid stores it (a temporary testing path, a dedicated item comes later); sneak right
 * clicking releases one back into the world. Storing is free, releasing has a five minute cooldown.</p>
 */
public class StandardIssueSwordItem extends SwordItem {

    /** 26 modifier + 4 netherite tier bonus + 1 player base = 31 attack damage. */
    private static final int ATTACK_DAMAGE = 26;

    /** 4.0 base attack speed - 2.2 = 1.8, a little faster than the netherite sword's 1.6. */
    private static final float ATTACK_SPEED = -2.2F;

    private static final double RELEASE_RANGE = 6.0D;

    public StandardIssueSwordItem(Item.Properties properties) {
        super(Tiers.NETHERITE, ATTACK_DAMAGE, ATTACK_SPEED, properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        return createStack();
    }

    public static ItemStack createStack() {
        ItemStack stack = new ItemStack(ModItems.STANDARD_ISSUE_SWORD.get());
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        return stack;
    }

}
