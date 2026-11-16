package com.curiats.returncapsule.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * Gives a humanoid ragdoll a real {@code PlayerModel} so its second skin layer survives.
 * <p>
 * Ragdollified resolves a mob's model through {@code ClientRagdollRenderer.humanoidModelFor}, which first asks its
 * capture cache and otherwise falls back to a plain humanoid model. A captured model is not a {@code PlayerModel}, so the
 * overlay pass in {@code renderModdedHumanoidOverlays} never reaches its {@code instanceof PlayerModel} branch — and that
 * branch is the only thing that knows about {@code jacket}, the sleeves and the trousers. The result is a corpse that
 * keeps the skin but silently loses the second layer.
 * <p>
 * Returning a genuine {@code PlayerModel} for our humanoid fixes it without touching their rendering code: the existing
 * overlay pass starts working on its own. The model is built once and reused, matching how their own player models are
 * handled, and only for this one mob type.
 */
@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientRagdollRenderer", remap = false)
public class RagdollifiedSkinLayerMixin {
    /** Entity id the ragdoll reports for our humanoid. */
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
