package com.curiats.returncapsule.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class Flare {

    public enum Layer {
        BEHIND,
        FRONT
    }

    public record Spec(ResourceLocation texture, double offsetY, float angle,
                       float minSize, float maxSize, Layer layer) {
    }

    public static void render(Entity target, float partialTick, PoseStack pose, MultiBufferSource buffer, Spec spec) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 center = target.getPosition(partialTick).add(0.0D, spec.offsetY(), 0.0D);
        float distance = (float) center.subtract(camera.getPosition()).length();
        float size = (float) Mth.clamp(distance * Math.tan(spec.angle()), spec.minSize(), spec.maxSize());

        pose.pushPose();
        pose.translate(0.0D, spec.offsetY(), 0.0D);
        Vec3 towardsCamera = camera.getPosition().subtract(center).scale(0.5D);
        pose.translate(towardsCamera.x, towardsCamera.y, towardsCamera.z);
        pose.mulPose(camera.rotation());
        Matrix4f matrix = pose.last().pose();
        RenderType type = spec.layer() == Layer.FRONT
                ? RenderType.eyes(spec.texture())
                : RenderType.entityTranslucent(spec.texture());
        Billboard.emit(buffer.getBuffer(type), matrix, size * 0.5F);
        pose.popPose();
    }

    private Flare() {
    }
}
