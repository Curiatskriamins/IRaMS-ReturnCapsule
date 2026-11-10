package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.block.CapsuleBlockEntity;
import com.curiats.returncapsule.block.TransportMarkerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ReturnCapsuleMod.MODID);

    public static final RegistryObject<BlockEntityType<CapsuleBlockEntity>> CAPSULE =
            BLOCK_ENTITIES.register("capsule", () -> BlockEntityType.Builder
                    .of(CapsuleBlockEntity::new, ModBlocks.CAPSULE.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TransportMarkerBlockEntity>> TRANSPORT_MARKER =
            BLOCK_ENTITIES.register("transport_marker", () -> BlockEntityType.Builder
                    .of(TransportMarkerBlockEntity::new, ModBlocks.TRANSPORT_MARKER.get())
                    .build(null));

    private ModBlockEntities() {
    }
}
