package com.curiats.returncapsule.client;

import com.curiats.returncapsule.block.TransportMarkerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;

public class TransportMarkerRenderer implements BlockEntityRenderer<TransportMarkerBlockEntity> {
    private static final float[] RED = {1.0F, 0.05F, 0.05F};

    public TransportMarkerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TransportMarkerBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof TransportCapsuleEntity) {
            return;
        }
        pose.pushPose();
        BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION,
                partialTick, 1.0F, blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime(),
                blockEntity.getBlockPos().getY(), 256, RED, 0.25F, 0.08F);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TransportMarkerBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
