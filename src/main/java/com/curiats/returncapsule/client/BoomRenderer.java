package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class BoomRenderer extends EntityRenderer<BoomEntity> {
    private static final ResourceLocation FLARE =
            ReturnCapsuleMod.id("textures/entity/boom_flare.png");
    private static final float MODEL_CENTER = 1.8F;
    private static final float MIN_SIZE = 2.0F;
    private static final float MAX_SIZE = 256.0F;
    private static final float ANGLE = 0.4F;

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

        pose.pushPose();
        pose.translate(0.0F, MODEL_CENTER * scale, 0.0F);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        pose.mulPose(camera.rotation());
        Matrix4f matrix = pose.last().pose();
        Vec3 center = new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()) + MODEL_CENTER * scale,
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        float distance = (float) center.subtract(camera.getPosition()).length();
        float size = (float) Mth.clamp(distance * Math.tan(ANGLE),
                MIN_SIZE * scale, MAX_SIZE * scale)
                * Config.BOOM_FLARE_SCALE.get().floatValue();
        float half = size * 0.5F;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(FLARE));
        consumer.vertex(matrix, -half, -half, 0.0F)
                .color(255, 255, 255, 255).uv(0.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(matrix, -half, half, 0.0F)
                .color(255, 255, 255, 255).uv(0.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(matrix, half, half, 0.0F)
                .color(255, 255, 255, 255).uv(1.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
        consumer.vertex(matrix, half, -half, 0.0F)
                .color(255, 255, 255, 255).uv(1.0F, 1.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(BoomEntity entity) {
        return false;
    }
}
