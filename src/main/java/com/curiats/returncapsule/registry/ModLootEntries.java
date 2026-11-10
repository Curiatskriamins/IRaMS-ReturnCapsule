package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.loot.ModItemLootEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootEntries {
    public static final DeferredRegister<LootPoolEntryType> ENTRY_TYPES =
            DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, ReturnCapsuleMod.MODID);

    public static final RegistryObject<LootPoolEntryType> MOD_ITEM =
            ENTRY_TYPES.register("mod_item", () -> new LootPoolEntryType(ModItemLootEntry.SERIALIZER));

    private ModLootEntries() {
    }
}
