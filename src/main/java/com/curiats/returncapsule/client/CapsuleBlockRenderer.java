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
    public CapsuleBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CapsuleBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int overlay) {
        Direction facing = blockEntity.getBlockState().getValue(CapsuleBlock.FACING);
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        
        
        
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot() + Config.MODEL_YAW_OFFSET.get().floatValue()));
        CapsuleGeoModel.get().render(pose, buffer, packedLight, overlay, blockEntity.getLidProgress(partialTick));
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
