package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.SupplyCapsuleEntity;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ReturnCapsuleMod.MODID);

    public static final RegistryObject<EntityType<SupplyCapsuleEntity>> SUPPLY_CAPSULE =
            ENTITIES.register("supply_capsule", () -> EntityType.Builder
                    .<SupplyCapsuleEntity>of(SupplyCapsuleEntity::new, MobCategory.MISC)
                    .sized(2.45F, 2.45F)
                    .clientTrackingRange(2000)
                    .updateInterval(1)
                    .build(ReturnCapsuleMod.id("supply_capsule").toString()));

    public static final RegistryObject<EntityType<TransportCapsuleEntity>> TRANSPORT_CAPSULE =
            ENTITIES.register("transport_capsule", () -> EntityType.Builder
                    .<TransportCapsuleEntity>of(TransportCapsuleEntity::new, MobCategory.MISC)
                    .sized(2.45F, 2.45F)
                    .clientTrackingRange(2000)
                    .updateInterval(1)
                    .build(ReturnCapsuleMod.id("transport_capsule").toString()));

    private ModEntities() {
    }
}
