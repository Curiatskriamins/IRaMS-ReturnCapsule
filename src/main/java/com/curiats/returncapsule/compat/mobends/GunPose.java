package com.curiats.returncapsule.compat.mobends;

import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import goblinbob.mobends.core.client.model.IModelPart;
import goblinbob.mobends.standard.data.BipedEntityData;

/**
 * Puts the arms into a two-handed hold when the humanoid is carrying a firearm.
 * <p>
 * Written as one static call rather than a bit of its own because every entry point in MoBends' own pose code is closed to
 * a gun: {@code BipedActionController.getAction} needs a crossbow use animation, {@code CrossbowHoldAnimationBit
 * .getChargedCrossbowArm} needs {@code UseAnim.CROSSBOW}, and its {@code perform} returns early on a null arm. Those are
 * item properties, not flags. Applying the rotations directly to the biped data, after the controller has run, is the only
 * place that is both reachable and last to write.
 */
public final class GunPose {
    private static final float TO_DEGREES = 180F / (float) Math.PI;
    private static final float HOLDING_PITCH = (-1.5707964F + 0.1F) * TO_DEGREES;
    private static final float SUPPORT_PITCH = -1.5F * TO_DEGREES;
    private static final float HOLDING_YAW = 0.3F * TO_DEGREES;
    private static final float SUPPORT_YAW = 0.6F * TO_DEGREES;
    private static final float SMOOTHNESS = 0.4F;

    private GunPose() {
    }

    public static void apply(Object rawData) {
        if (!(rawData instanceof BipedEntityData<?> data)) {
            return;
        }
        if (!(data.getEntity() instanceof NeutralHumanoid humanoid)
                || !TaczRifles.isGun(humanoid.getMainHandItem())) {
            return;
        }
        boolean rightHanded = humanoid.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        float headPitch = data.headPitch.get();
        float headYaw = data.headYaw.get();

        IModelPart holdingArm = rightHanded ? data.rightArm : data.leftArm;
        IModelPart supportArm = rightHanded ? data.leftArm : data.rightArm;
        IModelPart holdingForeArm = rightHanded ? data.rightForeArm : data.leftForeArm;
        IModelPart supportForeArm = rightHanded ? data.leftForeArm : data.rightForeArm;

        holdingArm.getRotation().setSmoothness(SMOOTHNESS)
                .orientX(HOLDING_PITCH + headPitch)
                .rotateY((rightHanded ? -HOLDING_YAW : HOLDING_YAW) + headYaw);
        supportArm.getRotation().setSmoothness(SMOOTHNESS)
                .orientX(SUPPORT_PITCH + headPitch)
                .rotateY((rightHanded ? SUPPORT_YAW : -SUPPORT_YAW) + headYaw);
        holdingForeArm.getRotation().setSmoothness(SMOOTHNESS).orientX(-5F);
        supportForeArm.getRotation().setSmoothness(SMOOTHNESS).orientX(-20F);

        data.renderRightItemRotation.setSmoothness(SMOOTHNESS).orientZero();
        data.renderLeftItemRotation.setSmoothness(SMOOTHNESS).orientZero();
    }
}
