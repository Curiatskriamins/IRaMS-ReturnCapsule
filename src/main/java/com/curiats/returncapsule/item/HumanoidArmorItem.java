package com.curiats.returncapsule.item;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.registry.ModArmorMaterials;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Armour piece of the standard issue set. It reuses the vanilla humanoid armour model, so no custom model is needed;
 * only the two layer textures are ours.
 *
 * <p>The set is issued, not salvaged: pieces never wear out. The tag is stamped on every stack that reaches a world, not
 * just on the default instance, because crafting, loot pools and the creative tab each build their stacks differently.</p>
 */
public class HumanoidArmorItem extends ArmorItem {

    public HumanoidArmorItem(ArmorItem.Type type, Item.Properties properties) {
        super(ModArmorMaterials.BASE_HUMANOID, type, properties);
    }

    @Override
    @Nullable
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return ReturnCapsuleMod.MODID + ":textures/models/armor/base_humanoid_layer_" + layer + ".png";
    }

    @Override
    public ItemStack getDefaultInstance() {
        return stamp(super.getDefaultInstance());
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        stamp(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!isUnbreakable(stack)) {
            stamp(stack);
        }
    }

    /** Vanilla asks this to decide whether a stack may take durability damage at all. */
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    private static boolean isUnbreakable(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("Unbreakable");
    }

    private static ItemStack stamp(ItemStack stack) {
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        return stack;
    }
}
