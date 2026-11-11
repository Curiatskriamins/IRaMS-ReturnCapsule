package com.curiats.returncapsule.client;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.client.geo.GeoModel;
import com.curiats.returncapsule.client.geo.GeoModelLoader;
import com.curiats.returncapsule.client.geo.GeoRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

 
public final class CapsuleGeoModel {
    private static final ResourceLocation GEO = ReturnCapsuleMod.id("geo/capsule.geo.json");
    public static final ResourceLocation TEXTURE = ReturnCapsuleMod.id("textures/entity/capsule.png");
    public static final ResourceLocation GLOW_TEXTURE = ReturnCapsuleMod.id("textures/entity/capsule_glow.png");

    private static final RenderType GLOW_LAYER = RenderType.entityTranslucent(GLOW_TEXTURE);

    private static volatile CapsuleGeoModel instance;

    private final GeoModel model;

    private CapsuleGeoModel(GeoModel model) {
        this.model = model;
    }

    public static CapsuleGeoModel get() {
        CapsuleGeoModel local = instance;
        if (local == null) {
            synchronized (CapsuleGeoModel.class) {
                local = instance;
                if (local == null) {
                    boolean hasGlow = Minecraft.getInstance().getResourceManager()
                            .getResource(GLOW_TEXTURE).isPresent();
                    local = new CapsuleGeoModel(GeoModelLoader.load(GEO, hasGlow));
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

    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, int overlay,
                       float lidProgress) {
        if (this.model.isEmpty()) {
            return;
        }
        GeoRenderer.render(this.model, pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight, overlay, lidProgress);
        if (this.model.hasGlow()) {
            GeoRenderer.render(this.model, pose, buffer.getBuffer(GLOW_LAYER),
                    LightTexture.FULL_BRIGHT, overlay, lidProgress);
        }
    }
}
