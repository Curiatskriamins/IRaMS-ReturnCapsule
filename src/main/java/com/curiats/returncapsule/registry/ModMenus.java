package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.menu.CapsuleMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ReturnCapsuleMod.MODID);

    public static final RegistryObject<MenuType<CapsuleMenu>> CAPSULE =
            MENUS.register("capsule", () -> new MenuType<>(CapsuleMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }
}
