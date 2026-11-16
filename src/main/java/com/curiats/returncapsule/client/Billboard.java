package com.curiats.returncapsule.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

final class Billboard {

    static void emit(VertexConsumer consumer, Matrix4f matrix, float half) {
        corner(consumer, matrix, -half, -half, 0.0F, 1.0F);
        corner(consumer, matrix, -half, half, 0.0F, 0.0F);
        corner(consumer, matrix, half, half, 1.0F, 0.0F);
        corner(consumer, matrix, half, -half, 1.0F, 1.0F);
    }

    private static void corner(VertexConsumer consumer, Matrix4f matrix,
                               float x, float y, float u, float v) {
        consumer.vertex(matrix, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    private Billboard() {
    }
}
