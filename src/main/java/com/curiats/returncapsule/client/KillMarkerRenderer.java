package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.BoomEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

public final class KillMarkerRenderer {
    private static final ResourceLocation TEXTURE =
            ReturnCapsuleMod.id("textures/entity/kill_ssag.png");
    private static final double ANGLE = 0.0085D;
    private static final float MIN = 0.02F;
    private static final float MAX = 64.0F;

    private static final RenderStateShard.TransparencyStateShard TRANSLUCENT =
            new RenderStateShard.TransparencyStateShard("returncapsule_translucent", () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderType MARKER = RenderType.create(
            "returncapsule_kill_marker",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            GameRenderer::getRendertypeEntityTranslucentShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, false, false))
                    .setTransparencyState(TRANSLUCENT)
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", GL11.GL_ALWAYS))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                    .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                    .createCompositeState(false));

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(MARKER);

        int bombs = 0;
        int markers = 0;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof BoomEntity bomb)) {
                continue;
            }
            bombs++;
            Vec3 anchor = anchorOf(minecraft, bomb, partial);
            if (anchor == null) {
                continue;
            }
            markers++;
            float distance = (float) anchor.subtract(cameraPos).length();
            float half = Mth.clamp(distance * (float) Math.tan(ANGLE), MIN, MAX) * 0.5F;
            pose.pushPose();
            pose.translate(anchor.x - cameraPos.x, anchor.y - cameraPos.y, anchor.z - cameraPos.z);
            pose.mulPose(camera.rotation());
            Matrix4f matrix = pose.last().pose();
            Billboard.emit(consumer, matrix, half);
            pose.popPose();
        }
        if (markers > 0) {
            buffer.endBatch(MARKER);
        }
    }

    @Nullable
    private static Vec3 anchorOf(Minecraft minecraft, BoomEntity bomb, float partial) {
        int targetId = bomb.clientLockedEntity();
        if (targetId != 0 && minecraft.level != null
                && minecraft.level.getEntity(targetId) instanceof LivingEntity target) {
            return new Vec3(Mth.lerp(partial, target.xOld, target.getX()),
                    target.getBoundingBox().maxY + 1.5D,
                    Mth.lerp(partial, target.zOld, target.getZ()));
        }
        Vec3 point = bomb.clientTargetPoint();
        if (point.x == 0.0D && point.y == 0.0D && point.z == 0.0D) {
            return null;
        }
        return point.add(0.0D, 1.5D, 0.0D);
    }

    private KillMarkerRenderer() {
    }
}
