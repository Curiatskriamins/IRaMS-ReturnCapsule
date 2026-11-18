package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.ragdoll.RagdollSkinSupport;
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

/**
 * Makes Ragdollified render our humanoid ragdoll with a player model, so the humanoid skin layer works.
 *
 * <p>The ragdoll parameter is declared as {@link Object} (a Mixin surrogate type) and its mob type is read by
 * name through {@link RagdollSkinSupport}. Ragdollified therefore stays optional: this file names no
 * Ragdollified type, and {@link Pseudo} plus {@code require = 0} keep it inert when the mod is absent.</p>
 */
@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientRagdollRenderer", remap = false)
public class RagdollifiedSkinLayerMixin {

    private static PlayerModel<?> returncapsule$playerModel;

    @Inject(method = "humanoidModelFor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$usePlayerModel(Object ragdoll,
                                                    CallbackInfoReturnable<HumanoidModel<?>> callback) {
        if (!RagdollSkinSupport.isHumanoidRagdoll(ragdoll)) {
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
