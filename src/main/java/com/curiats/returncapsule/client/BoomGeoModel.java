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

/**
 * Cached geometry of the cruise bomb. The file lives in {@code geo/boom.geo.json} and was
 * converted from the Bedrock model in {@code sjjd/导弹/boom.geo.json}.
 *
 * <p>Like the capsule, the bomb has an optional emissive layer: {@code boom_glow.png} is a
 * transparent mask that keeps only the red parts, and it is drawn a second time with an
 * emissive render type at full brightness. Remove that texture in a resource pack to disable
 * the glow.</p>
 */
public final class BoomGeoModel {
    private static final ResourceLocation GEO = ReturnCapsuleMod.id("geo/boom.geo.json");
    public static final ResourceLocation TEXTURE = ReturnCapsuleMod.id("textures/entity/boom.png");
    public static final ResourceLocation GLOW_TEXTURE =
            ReturnCapsuleMod.id("textures/entity/boom_glow.png");

    private static final RenderType GLOW_LAYER = RenderType.entityTranslucentEmissive(GLOW_TEXTURE);

    private static volatile BoomGeoModel instance;

    private final GeoModel model;

    private BoomGeoModel(GeoModel model) {
        this.model = model;
    }

    public static BoomGeoModel get() {
        BoomGeoModel local = instance;
        if (local == null) {
            synchronized (BoomGeoModel.class) {
                local = instance;
                if (local == null) {
                    boolean hasGlow = Minecraft.getInstance().getResourceManager()
                            .getResource(GLOW_TEXTURE).isPresent();
                    local = new BoomGeoModel(GeoModelLoader.load(GEO, hasGlow));
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
        GeoRenderer.render(this.model, pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight, overlay, 0.0F);
        if (this.model.hasGlow()) {
            GeoRenderer.render(this.model, pose, buffer.getBuffer(GLOW_LAYER),
                    LightTexture.FULL_BRIGHT, overlay, 0.0F);
        }
    }
}
