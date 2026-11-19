package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.item.CapsuleBlockItem;
import com.curiats.returncapsule.item.CapsuleCallerItem;
import com.curiats.returncapsule.item.HumanoidArmorItem;
import com.curiats.returncapsule.item.KillMarkerItem;
import com.curiats.returncapsule.item.TransportDesignatorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ReturnCapsuleMod.MODID);

    public static final RegistryObject<Item> CAPSULE_CALLER = ITEMS.register("capsule_caller",
            () -> new CapsuleCallerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockItem> CAPSULE = ITEMS.register("capsule",
            () -> new CapsuleBlockItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TRANSPORT_DESIGNATOR = ITEMS.register("transport_designator",
            () -> new TransportDesignatorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> KILL_MARKER_INDICATOR = ITEMS.register("kill_marker_indicator",
            () -> new KillMarkerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BASE_HUMANOID_SPAWN_EGG = ITEMS.register("base_humanoid_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BASE_HUMANOID, 0xE4E4E4, 0x2A2A2A,
                    new Item.Properties()));

    public static final RegistryObject<Item> BASE_HUMANOID_HELMET = ITEMS.register("base_humanoid_helmet",
            () -> new HumanoidArmorItem(ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<Item> BASE_HUMANOID_CHESTPLATE = ITEMS.register("base_humanoid_chestplate",
            () -> new HumanoidArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final RegistryObject<Item> BASE_HUMANOID_LEGGINGS = ITEMS.register("base_humanoid_leggings",
            () -> new HumanoidArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static final RegistryObject<Item> BASE_HUMANOID_BOOTS = ITEMS.register("base_humanoid_boots",
            () -> new HumanoidArmorItem(ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final RegistryObject<Item> STANDARD_ISSUE_SWORD = ITEMS.register("standard_issue_sword",
            () -> new com.curiats.returncapsule.item.StandardIssueSwordItem(new Item.Properties()));

    private ModItems() {
    }
}
