package com.curiats.returncapsule.registry;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.HumanoidProfile;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Attributes are per entity type, so each profile gets its own supplier built from the same code path.
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {
    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BASE_HUMANOID.get(),
                NeutralHumanoid.createAttributes(HumanoidProfile.BASE).build());
    }

    private ModEntityAttributes() {
    }
}
