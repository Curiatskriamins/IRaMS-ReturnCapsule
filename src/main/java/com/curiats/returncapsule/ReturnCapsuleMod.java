package com.curiats.returncapsule;

import com.mojang.logging.LogUtils;
import com.curiats.returncapsule.registry.ModBlockEntities;
import com.curiats.returncapsule.registry.ModBlocks;
import com.curiats.returncapsule.registry.ModEffects;
import com.curiats.returncapsule.registry.ModEntities;
import com.curiats.returncapsule.registry.ModItems;
import com.curiats.returncapsule.registry.ModLootEntries;
import com.curiats.returncapsule.registry.ModMenus;
import com.curiats.returncapsule.registry.ModSounds;
import com.curiats.returncapsule.network.ModNetwork;
import com.curiats.returncapsule.registry.ModTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ReturnCapsuleMod.MODID)
public final class ReturnCapsuleMod {
    public static final String MODID = "returncapsule";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReturnCapsuleMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModTabs.TABS.register(modBus);
        ModLootEntries.ENTRY_TYPES.register(modBus);
        ModNetwork.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
