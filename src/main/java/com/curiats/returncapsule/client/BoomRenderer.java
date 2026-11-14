package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class BoomRenderer extends EntityRenderer<BoomEntity> {
    private static final ResourceLocation KILL_MARKER =
            ReturnCapsuleMod.id("textures/entity/kill_ssag.png");
    private static final double MARKER_ANGLE = 0.0085D;
    private static final float MARKER_MIN = 0.02F;
    private static final float MARKER_MAX = 64.0F;
    private static final RenderType MARKER_TYPE = RenderType.create(
            "returncapsule_kill_marker",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderShards.ENTITY_TRANSLUCENT)
                    .setTextureState(new RenderStateShard.TextureStateShard(KILL_MARKER, false, false))
                    .setTransparencyState(RenderShards.TRANSLUCENT)
                    .setDepthTestState(RenderShards.NO_DEPTH)
                    .setCullState(RenderShards.CULL_NONE)
                    .setLightmapState(RenderShards.LIGHTMAP)
                    .setOverlayState(RenderShards.OVERLAY)
                    .createCompositeState(false));

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

        int targetId = entity.clientLockedEntity();
        if (targetId == 0 || !(entity.level().getEntity(targetId) instanceof LivingEntity target)) {
            return;
        }
        double tx = Mth.lerp(partialTick, target.xOld, target.getX());
        double ty = target.getBoundingBox().maxY + 1.5D;
        double tz = Mth.lerp(partialTick, target.zOld, target.getZ());
        pose.pushPose();
        pose.translate(tx - entity.getX(), ty - entity.getY(), tz - entity.getZ());
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        pose.mulPose(camera.rotation());
        Matrix4f matrix = pose.last().pose();
        Vec3 center = new Vec3(tx, ty, tz);
        float distance = (float) center.subtract(camera.getPosition()).length();
        float size = (float) Mth.clamp(distance * Math.tan(MARKER_ANGLE), MARKER_MIN, MARKER_MAX);
        float half = size * 0.5F;
        VertexConsumer consumer = buffer.getBuffer(MARKER_TYPE);
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
