package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ReturnCapsuleMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.returncapsule"))
                    .icon(() -> new ItemStack(ModItems.CAPSULE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CAPSULE_CALLER.get());
                        output.accept(ModItems.TRANSPORT_DESIGNATOR.get());
                        output.accept(ModItems.KILL_MARKER_INDICATOR.get());
                        output.accept(ModItems.CAPSULE.get());
                        output.accept(ModItems.MANNED_CAPSULE.get());
                        output.accept(ModItems.STANDARD_STORAGE_ANCHOR.get());
                        output.accept(ModItems.BASE_HUMANOID_SPAWN_EGG.get());
                        output.accept(ModItems.BASE_HUMANOID_HELMET.get());
                        output.accept(ModItems.BASE_HUMANOID_CHESTPLATE.get());
                        output.accept(ModItems.BASE_HUMANOID_LEGGINGS.get());
                        output.accept(ModItems.BASE_HUMANOID_BOOTS.get());
                        output.accept(com.curiats.returncapsule.item.StandardIssueSwordItem.createStack());
                    })
                    .build());

    private ModTabs() {
    }
}
