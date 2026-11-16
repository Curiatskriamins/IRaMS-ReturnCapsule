package com.curiats.returncapsule.mixin;

import com.raiiiden.ragdollified.client.ClientRagdoll;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientRagdollRenderer", remap = false)
public class RagdollifiedSkinLayerMixin {

    private static final String HUMAN0ID_TYPE = "returncapsule:base_humanoid";

    private static PlayerModel<?> returncapsule$playerModel;

    @Inject(method = "humanoidModelFor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$usePlayerModel(ClientRagdoll ragdoll,
                                                    CallbackInfoReturnable<HumanoidModel<?>> callback) {
        if (ragdoll == null || !HUMAN0ID_TYPE.equals(ragdoll.getMobType())) {
            return;
        }
        if (returncapsule$playerModel == null) {
            MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
            LayerDefinition definition = LayerDefinition.create(mesh, 64, 64);
            returncapsule$playerModel = new PlayerModel<>(definition.bakeRoot(), false);
        }
        callback.setReturnValue((HumanoidModel<?>) returncapsule$playerModel);
    }

    private RagdollifiedSkinLayerMixin() {
    }
}
