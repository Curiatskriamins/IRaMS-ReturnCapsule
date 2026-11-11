package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.BoomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

 
public class BoomRenderer extends EntityRenderer<BoomEntity> {
    public BoomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(BoomEntity entity) {
        return BoomGeoModel.TEXTURE;
    }

    @Override
    public void render(BoomEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        
        
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.XP.rotationDegrees(
                Config.BOOM_MODEL_BASE_PITCH.get().floatValue()));
        float scale = Config.BOOM_MODEL_SCALE.get().floatValue();
        pose.scale(scale, scale, scale);
        BoomGeoModel.get().render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(BoomEntity entity) {
        return false;
    }
}
