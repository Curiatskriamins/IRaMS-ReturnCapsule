package com.curiats.returncapsule.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.block.CapsuleBlock;
import com.curiats.returncapsule.block.CapsuleBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class CapsuleBlockRenderer implements BlockEntityRenderer<CapsuleBlockEntity> {

    private static final java.util.concurrent.atomic.AtomicInteger LID_LOGS =
            new java.util.concurrent.atomic.AtomicInteger();
    public CapsuleBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CapsuleBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int overlay) {
        Direction facing = blockEntity.getBlockState().getValue(CapsuleBlock.FACING);
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);

        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot() + Config.MODEL_YAW_OFFSET.get().floatValue()));
        float lidProgress = blockEntity.getLidProgress(partialTick);
        if (lidProgress > 0.0F && lidProgress < 1.0F) {
            if (LID_LOGS.get() < 12) {
                LID_LOGS.incrementAndGet();
                com.curiats.returncapsule.ReturnCapsuleMod.LOGGER.info("[lid] progress={} state={}",
                        String.format("%.3f", lidProgress), blockEntity.getAnimState());
            }
        } else {
            LID_LOGS.set(0);
        }
        CapsuleGeoModel.get().render(pose, buffer, packedLight, overlay, lidProgress);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CapsuleBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
