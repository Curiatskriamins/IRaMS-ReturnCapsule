package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomEntity;
import com.curiats.returncapsule.entity.SupplyCapsuleEntity;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import com.curiats.returncapsule.entity.humanoid.BaseHumanoid;
import com.curiats.returncapsule.entity.humanoid.HumanoidProfile;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ReturnCapsuleMod.MODID);

    public static final RegistryObject<EntityType<NeutralHumanoid>> BASE_HUMANOID =
            ENTITIES.register("base_humanoid", () -> humanoid(HumanoidProfile.BASE));

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
                    // The manned model is 2.19 blocks wide at author scale and rendered at twice that, so the box
                    // matches its footprint the same way the supply capsule's box matches its own model width.
                    .sized(4.4F, 4.4F)
                    .clientTrackingRange(2000)
                    .updateInterval(1)
                    .build(ReturnCapsuleMod.id("transport_capsule").toString()));

    public static final RegistryObject<EntityType<BoomEntity>> BOOM =
            ENTITIES.register("boom", () -> EntityType.Builder
                    .<BoomEntity>of(BoomEntity::new, MobCategory.MISC)
                    .sized(1.2F, 1.2F)
                    .clientTrackingRange(512)
                    .updateInterval(1)
                    .build(ReturnCapsuleMod.id("boom").toString()));

    private static EntityType<NeutralHumanoid> humanoid(HumanoidProfile profile) {
        return EntityType.Builder
                .<NeutralHumanoid>of((type, level) -> new BaseHumanoid(type, level),
                        MobCategory.CREATURE)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(10)
                .build(ReturnCapsuleMod.id(profile.id() + "_humanoid").toString());
    }

    private ModEntities() {
    }
}
