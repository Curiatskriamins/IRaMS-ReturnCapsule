package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, value = Dist.CLIENT)
public final class BoomFlareOverlay {
    private static final ResourceLocation FLARE =
            ReturnCapsuleMod.id("textures/entity/boom_flare.png");
    private static final double FLARE_ANGLE = 0.8D;
    private static final double FLARE_BASE = 4.0D;
    private static final double FLARE_MAX = 256.0D;
    private static final float MIN_ALPHA = 0.35F;
    private static final float FADE_DISTANCE = 512.0F;
    private static final int FULL_BRIGHT = 0xF000F0;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 cameraPos = event.getCamera().getPosition();
        BoomEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof BoomEntity bomb) || !bomb.isAlive()) {
                continue;
            }
            double distance = bomb.position().distanceToSqr(cameraPos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = bomb;
            }
        }
        if (nearest == null) {
            return;
        }

        float scale = Config.BOOM_MODEL_SCALE.get().floatValue();
        Vec3 tail = nearest.position().subtract(nearest.moveDirection().scale(1.55D * scale));
        double distance = Math.sqrt(nearestDistance);
        double size = Mth.clamp(distance * Math.tan(FLARE_ANGLE),
                FLARE_BASE * scale, FLARE_MAX * scale);
        float alpha = Mth.clamp(1.0F - (float) distance / FADE_DISTANCE, MIN_ALPHA, 1.0F);
        AbstractTexture flareTexture = minecraft.getTextureManager().getTexture(FLARE);
        flareTexture.setFilter(false, false);

        renderFlare(event.getPoseStack(), event.getCamera(), tail, (float) size, alpha);
    }

    private static void renderFlare(PoseStack pose, Camera camera, Vec3 tail, float size, float alpha) {
        Vec3 relative = tail.subtract(camera.getPosition());
        Quaternionf cameraRotation = camera.rotation();
        Vector3f viewOffset = cameraRotation.conjugate(new Quaternionf()).transform(
                new Vector3f((float) relative.x, (float) relative.y, (float) relative.z));
        pose.pushPose();
        
        
        
        pose.translate(viewOffset.x, viewOffset.y, viewOffset.z);
        Matrix4f matrix = pose.last().pose();
        float half = size * 0.5F;
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader);
        RenderSystem.setShaderTexture(0, FLARE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        buffer.vertex(matrix, -half, -half, 0.0F)
                .color(255, 255, 255, a).uv(0.0F, 1.0F).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, -half, half, 0.0F)
                .color(255, 255, 255, a).uv(0.0F, 0.0F).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, half, half, 0.0F)
                .color(255, 255, 255, a).uv(1.0F, 0.0F).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, half, -half, 0.0F)
                .color(255, 255, 255, a).uv(1.0F, 1.0F).uv2(FULL_BRIGHT).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        pose.popPose();
    }

    private BoomFlareOverlay() {
    }
}
