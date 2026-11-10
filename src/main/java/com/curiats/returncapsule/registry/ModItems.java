package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.item.CapsuleBlockItem;
import com.curiats.returncapsule.item.CapsuleCallerItem;
import com.curiats.returncapsule.item.TransportDesignatorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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

    private ModItems() {
    }
}
