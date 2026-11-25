package com.curiats.returncapsule.client;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.client.geo.GeoModel;
import com.curiats.returncapsule.client.geo.GeoModelLoader;
import com.curiats.returncapsule.client.geo.GeoRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * The manned return capsule: same custom geometry pipeline as the supply capsule, twice the size and without the lid.
 *
 * <p>The four retro rockets are Blockbench locator cubes; the loader keeps their centres, so the same model data also
 * gives the exact nozzle positions for the landing particles.</p>
 */
public final class MannedCapsuleGeoModel {

    private static final ResourceLocation GEO = ReturnCapsuleMod.id("geo/manned_capsule.geo.json");

    public static final ResourceLocation TEXTURE = ReturnCapsuleMod.id("textures/entity/manned_capsule.png");

    public static final ResourceLocation GLOW_TEXTURE = ReturnCapsuleMod.id("textures/entity/manned_capsule_glow.png");

    private static final RenderType GLOW_LAYER = RenderType.entityTranslucent(GLOW_TEXTURE);

    /** The manned capsule is modelled at half the intended in world size. */
    private static final float SCALE = 2.0F;

    private static final float MODEL_SCALE = 1.0F / 16.0F;

    private static volatile MannedCapsuleGeoModel instance;

    private final GeoModel model;

    private MannedCapsuleGeoModel(GeoModel model) {
        this.model = model;
    }

    public static MannedCapsuleGeoModel get() {
        MannedCapsuleGeoModel local = instance;
        if (local == null) {
            synchronized (MannedCapsuleGeoModel.class) {
                local = instance;
                if (local == null) {
                    boolean hasGlow = Minecraft.getInstance().getResourceManager()
                            .getResource(GLOW_TEXTURE).isPresent();
                    local = new MannedCapsuleGeoModel(GeoModelLoader.load(GEO, hasGlow));
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void reload() {
        instance = null;
        get();
    }

    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, int overlay) {
        if (this.model.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.scale(SCALE, SCALE, SCALE);
        GeoRenderer.render(this.model, pose, buffer.getBuffer(RenderType.entityTranslucent(TEXTURE)),
                packedLight, overlay, 0.0F);
        if (this.model.hasGlow()) {
            GeoRenderer.render(this.model, pose, buffer.getBuffer(GLOW_LAYER),
                    net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 0.0F);
        }
        pose.popPose();
    }

    /** Visits the four retro rocket nozzle positions in world space. */
    public static void forEachThruster(Entity entity, Consumer<Vec3> consumer) {
        MannedCapsuleGeoModel local = get();
        float[] points = local.model.markerPoints();
        if (points.length < 3) {
            return;
        }
        double yaw = Math.toRadians(180.0D - entity.getYRot() + Config.MODEL_YAW_OFFSET.get().floatValue());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double pitch = Math.toRadians(entity instanceof com.curiats.returncapsule.entity.TransportCapsuleEntity capsule
                ? TransportAttitude.pitchDegrees(capsule) : 0.0F);
        double cosP = Math.cos(pitch);
        double sinP = Math.sin(pitch);
        for (int index = 0; index + 2 < points.length; index += 3) {
            double x = SCALE * (points[index] - local.model.centerX()) * MODEL_SCALE;
            double y = SCALE * (points[index + 1] - local.model.centerY()) * MODEL_SCALE;
            double z = SCALE * (points[index + 2] - local.model.centerZ()) * MODEL_SCALE;
            // Pitch about X first, then the yaw used by the renderer.
            double pitchedY = y * cosP - z * sinP;
            double pitchedZ = y * sinP + z * cosP;
            double worldX = x * cos + pitchedZ * sin;
            double worldZ = -x * sin + pitchedZ * cos;
            consumer.accept(entity.position().add(worldX, pitchedY, worldZ));
        }
    }

}
