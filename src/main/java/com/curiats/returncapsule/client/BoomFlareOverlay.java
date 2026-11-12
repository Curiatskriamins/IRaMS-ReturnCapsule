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
import org.joml.Vector4f;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, value = Dist.CLIENT)
public final class BoomFlareOverlay {
    private static final ResourceLocation FLARE =
            ReturnCapsuleMod.id("textures/entity/boom_flare.png");
    private static final float SIZE_FACTOR = 1.1F;
    private static final float MIN_ALPHA = 0.35F;
    private static final float FADE_DISTANCE = 512.0F;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int MAX_FLARES = 32;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Matrix4f view = new Matrix4f().rotation(camera.rotation()).invert();
        Matrix4f projection = event.getProjectionMatrix();
        float width = minecraft.getWindow().getGuiScaledWidth();
        float height = minecraft.getWindow().getGuiScaledHeight();
        float size = height * SIZE_FACTOR;
        int count = 0;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof BoomEntity bomb) || !bomb.isAlive()) {
                continue;
            }
            if (++count > MAX_FLARES) {
                break;
            }
            Vec3 tail = bomb.position().subtract(bomb.moveDirection()
                    .scale(1.55D * Config.BOOM_MODEL_SCALE.get().floatValue()));
            Vec3 relative = tail.subtract(camera.getPosition());
            Vector4f clip = new Vector4f((float) relative.x, (float) relative.y,
                    (float) relative.z, 1.0F);
            view.transform(clip);
            projection.transform(clip);
            if (clip.w <= 0.0F) {
                continue;
            }
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (ndcX < -1.5F || ndcX > 1.5F || ndcY < -1.5F || ndcY > 1.5F) {
                continue;
            }
            float screenX = (ndcX * 0.5F + 0.5F) * width;
            float screenY = (1.0F - (ndcY * 0.5F + 0.5F)) * height;
            float distance = (float) Math.sqrt(bomb.position().distanceToSqr(camera.getPosition()));
            float alpha = Mth.clamp(1.0F - distance / FADE_DISTANCE, MIN_ALPHA, 1.0F);
            renderFlare(screenX, screenY, size, alpha);
        }
    }

    private static void renderFlare(float x, float y, float size, float alpha) {
        AbstractTexture flareTexture = Minecraft.getInstance().getTextureManager().getTexture(FLARE);
        flareTexture.setFilter(false, false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader);
        RenderSystem.setShaderTexture(0, FLARE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        PoseStack pose = new PoseStack();
        Matrix4f matrix = pose.last().pose();
        float half = size * 0.5F;
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        buffer.vertex(matrix, x - half, y - half, 0.0F)
                .color(255, 255, 255, a).uv(0.0F, 1.0F).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, x - half, y + half, 0.0F)
                .color(255, 255, 255, a).uv(0.0F, 0.0F).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, x + half, y + half, 0.0F)
                .color(255, 255, 255, a).uv(1.0F, 0.0F).uv2(FULL_BRIGHT).endVertex();
        buffer.vertex(matrix, x + half, y - half, 0.0F)
                .color(255, 255, 255, a).uv(1.0F, 1.0F).uv2(FULL_BRIGHT).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private BoomFlareOverlay() {
    }
}
