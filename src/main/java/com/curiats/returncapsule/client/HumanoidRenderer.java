package com.curiats.returncapsule.client;

import com.curiats.returncapsule.compat.mobends.MobendsAddon;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a humanoid with the vanilla player model in its classic form ({@code slim = false}, the 4px arms), which
 * keeps it on the same skeleton that player animation mods operate on.
 * <p>
 * {@link HumanoidMobRenderer} already installs the held-item, head and elytra layers, so a rifle or a blade in the
 * entity's hand renders through the normal item renderer.
 * <p>
 * The model is scaled by {@code 0.9375} exactly as {@code PlayerRenderer} does. Without it the raw 32-unit player mesh
 * renders 2.0 blocks tall and the humanoid looks noticeably taller than a player, whose renderer shrinks it to 1.875.
 */
public class HumanoidRenderer extends HumanoidMobRenderer<NeutralHumanoid, PlayerModel<NeutralHumanoid>> {
    /** Same factor {@code PlayerRenderer.scale} applies, so the humanoid matches a player's silhouette. */
    private static final float PLAYER_SCALE = 0.9375F;

    public HumanoidRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    protected void scale(NeutralHumanoid entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
    }

    /**
     * Drives MoBends' attack animation once per swing.
     * <p>
     * The hook lives here because this runs on the client for exactly the entities that are being animated, and
     * {@code swingTime == 1} is the single frame at which a new swing is one tick old, so one swing is reported once no
     * matter how many render passes happen.
     */
    @Override
    public void render(NeutralHumanoid entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (MobendsAddon.isNewSwing(entity)) {
            MobendsAddon.driveAttack(entity);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(NeutralHumanoid entity) {
        return entity.profile().skin();
    }
}
