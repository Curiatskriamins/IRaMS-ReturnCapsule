package com.curiats.returncapsule.client.armor;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.PlayerEquipment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Second render pass over the armour geometry.
 *
 * <p>Vanilla renders one texture per armour slot, so decals, trim and glowing strips cannot live on the same part as
 * the plating. This layer renders the same armour model again with an overlay texture, which keeps the base pass
 * untouched (and therefore compatible with every armour mod) while giving the set its second texture.</p>
 *
 * <p>It is added as a render layer, never as a renderer replacement, and it bails out silently unless the player wears
 * the full set and the parent model is a player model.</p>
 */
public class HumanoidArmorOverlayLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation OUTER = ReturnCapsuleMod.id(
            "textures/models/armor/baseplayer_overlay_layer_1.png");
    private static final ResourceLocation INNER = ReturnCapsuleMod.id(
            "textures/models/armor/baseplayer_overlay_layer_2.png");

    private HumanoidModel<AbstractClientPlayer> outer;
    private HumanoidModel<AbstractClientPlayer> inner;

    public HumanoidArmorOverlayLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!PlayerEquipment.wearingFullSet(player)
                || !com.curiats.returncapsule.Config.EQUIPMENT_OVERLAY_LAYER.get()) {
            return;
        }
        PlayerModel<AbstractClientPlayer> parent = this.getParentModel();
        if (parent == null) {
            return;
        }
        this.outer = this.outer == null ? bake(ModelLayers.PLAYER_OUTER_ARMOR) : this.outer;
        this.inner = this.inner == null ? bake(ModelLayers.PLAYER_INNER_ARMOR) : this.inner;

        copyPose(this.outer, parent);
        VertexConsumer outerBuffer = buffer.getBuffer(RenderType.armorCutoutNoCull(OUTER));
        this.outer.renderToBuffer(pose, outerBuffer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        copyPose(this.inner, parent);
        VertexConsumer innerBuffer = buffer.getBuffer(RenderType.armorCutoutNoCull(INNER));
        this.inner.renderToBuffer(pose, innerBuffer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Copies the parent's limb poses onto the baked armour model. Done part by part instead of a whole model copy so a
     * mod that swaps the player model for its own subclass still works, as long as the standard parts exist.
     */
    private static void copyPose(HumanoidModel<?> target, PlayerModel<?> source) {
        target.head.copyFrom(source.head);
        target.hat.copyFrom(source.hat);
        target.body.copyFrom(source.body);
        target.rightArm.copyFrom(source.rightArm);
        target.leftArm.copyFrom(source.leftArm);
        target.rightLeg.copyFrom(source.rightLeg);
        target.leftLeg.copyFrom(source.leftLeg);
    }

    private static HumanoidModel<AbstractClientPlayer> bake(net.minecraft.client.model.geom.ModelLayerLocation location) {
        return new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(location));
    }
}
