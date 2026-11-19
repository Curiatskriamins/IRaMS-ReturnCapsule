package com.curiats.returncapsule.item;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.registry.ModArmorMaterials;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Armour piece of the base humanoid set. It reuses the vanilla humanoid armour model, so no custom model is needed;
 * only the two layer textures are ours.
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
}
