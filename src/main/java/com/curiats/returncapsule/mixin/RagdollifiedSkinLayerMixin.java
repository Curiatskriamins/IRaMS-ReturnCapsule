package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.compat.ragdoll.RagdollSkinSupport;
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

/**
 * Makes Ragdollified render our humanoid ragdoll with a player model, so the humanoid skin layer works.
 *
 * <p>The {@code ClientRagdoll} parameter type comes from the compile only {@code compatStub} source set, not from
 * a Ragdollified jar: Mixin validates this handler descriptor exactly, so the parameter cannot be widened to
 * {@code Object}. The stub is never packaged and no member is called on it - the mob type is read reflectively
 * through {@link RagdollSkinSupport}, and {@link Pseudo} plus {@code require = 0} keep the mixin inert when
 * Ragdollified is absent.</p>
 */
@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientRagdollRenderer", remap = false)
public class RagdollifiedSkinLayerMixin {

    private static PlayerModel<?> returncapsule$playerModel;

    @Inject(method = "humanoidModelFor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void returncapsule$usePlayerModel(ClientRagdoll ragdoll,
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
