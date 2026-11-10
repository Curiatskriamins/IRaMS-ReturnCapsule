package com.curiats.returncapsule.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class TransportCapsuleRenderer extends EntityRenderer<TransportCapsuleEntity> {
    public TransportCapsuleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.6F;
        this.shadowStrength = 0.9F;
    }

    @Override
    public ResourceLocation getTextureLocation(TransportCapsuleEntity entity) {
        return CapsuleGeoModel.TEXTURE;
    }

    @Override
    public void render(TransportCapsuleEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        CapsuleGeoModel.get().render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY, 0.0F);
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(TransportCapsuleEntity entity) {
        return false;
    }
}
