package com.curiats.returncapsule.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

 
public final class GeoRenderer {
    private static final float MODEL_SCALE = 0.0625F;
    private static final Vector4f POSITION = new Vector4f();
    private static final Vector3f NORMAL = new Vector3f();

    public static void render(GeoModel model, PoseStack pose, VertexConsumer consumer,
                              int packedLight, int overlay, float lidProgress) {
        if (model.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        pose.translate(-model.centerX(), -model.centerY(), -model.centerZ());

        Matrix4f matrix = pose.last().pose();
        Matrix3f normalMatrix = pose.last().normal();
        emit(model.staticFaces(), matrix, normalMatrix, consumer, packedLight, overlay, 0.0F, 0.0F, 0.0F);

        GeoFace[] lidFaces = model.lidFaces();
        if (lidFaces.length > 0) {
            float eased = 1.0F - Mth.cos(Mth.clamp(lidProgress, 0.0F, 1.0F) * ((float) Math.PI * 0.5F));
            emit(lidFaces, matrix, normalMatrix, consumer, packedLight, overlay,
                    model.lidOffsetX() * eased,
                    model.lidOffsetY() * eased,
                    model.lidOffsetZ() * eased);
        }
        pose.popPose();
    }

    private static void emit(GeoFace[] faces, Matrix4f matrix, Matrix3f normalMatrix,
                             VertexConsumer consumer, int packedLight, int overlay,
                             float shiftX, float shiftY, float shiftZ) {
        for (GeoFace face : faces) {
            
            NORMAL.set(face.normalX(), face.normalY(), face.normalZ());
            normalMatrix.transform(NORMAL);
            NORMAL.normalize();
            float normalX = NORMAL.x;
            float normalY = NORMAL.y;
            float normalZ = NORMAL.z;

            float[] positions = face.positions();
            float[] uvs = face.uvs();
            for (int vertex = 0; vertex < 4; vertex++) {
                int base = vertex * 3;
                POSITION.set(positions[base] + shiftX,
                        positions[base + 1] + shiftY,
                        positions[base + 2] + shiftZ, 1.0F);
                matrix.transform(POSITION);
                
                
                consumer.vertex(POSITION.x, POSITION.y, POSITION.z)
                        .color(255, 255, 255, 255)
                        .uv(uvs[vertex * 2], uvs[vertex * 2 + 1])
                        .overlayCoords(overlay)
                        .uv2(packedLight)
                        .normal(normalX, normalY, normalZ)
                        .endVertex();
            }
        }
    }

    private GeoRenderer() {
    }
}
