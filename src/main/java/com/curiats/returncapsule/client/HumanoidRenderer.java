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

public class HumanoidRenderer extends HumanoidMobRenderer<NeutralHumanoid, PlayerModel<NeutralHumanoid>> {

    private static final float PLAYER_SCALE = 0.9375F;

    public HumanoidRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    protected void scale(NeutralHumanoid entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
    }

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
