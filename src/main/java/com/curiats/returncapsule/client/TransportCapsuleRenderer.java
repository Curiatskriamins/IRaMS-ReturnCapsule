package com.curiats.returncapsule.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class TransportCapsuleRenderer extends EntityRenderer<TransportCapsuleEntity> {
    private static final ResourceLocation FLARE = com.curiats.returncapsule.ReturnCapsuleMod
            .id("textures/entity/boom_flare.png");
    private static final float FLARE_ANGLE = 0.4F;
    private static final float FLARE_MIN = 2.0F;
    private static final float FLARE_MAX = 256.0F;
    private static final double FLARE_CUTOFF = 60.0D;

    /** The flare is not drawn while the capsule is still this far above the observer. */
    private static final double FLARE_MAX_ABOVE_PLAYER = 500.0D;
    private static final float FLARE_HEIGHT = 5.0F;

    private static final java.util.Set<java.util.UUID> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public TransportCapsuleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.6F;
        this.shadowStrength = 0.9F;
    }

    @Override
    public ResourceLocation getTextureLocation(TransportCapsuleEntity entity) {
        return MannedCapsuleGeoModel.TEXTURE;
    }

    private static final com.curiats.returncapsule.client.Flare.Spec FLARE_SPEC =
            new com.curiats.returncapsule.client.Flare.Spec(FLARE, 5.0D, 0.4F, 2.0F, 256.0F,
                    com.curiats.returncapsule.client.Flare.Layer.BEHIND);

    @Override
    public void render(TransportCapsuleEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        if (shouldDrawFlare(entity)) {
            Flare.render(entity, partialTick, pose, buffer, FLARE_SPEC);
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw + Config.MODEL_YAW_OFFSET.get().floatValue()));
        pose.mulPose(Axis.XP.rotationDegrees(TransportAttitude.pitchDegrees(entity)));
        // The manned capsule uses its own geometry, rendered at twice the size; the pose stack offset stays the
        // same so the entity origin is still the hull's base.
        MannedCapsuleGeoModel.get().render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static boolean shouldDrawFlare(TransportCapsuleEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.getVehicle() == entity) {
            return false;
        }
        double gap = entity.getY() - entity.landingY();
        double abovePlayer = entity.getY() - minecraft.player.getY();
        return gap > FLARE_CUTOFF && abovePlayer < FLARE_MAX_ABOVE_PLAYER;
    }

    @Override
    protected boolean shouldShowName(TransportCapsuleEntity entity) {
        return false;
    }
}
