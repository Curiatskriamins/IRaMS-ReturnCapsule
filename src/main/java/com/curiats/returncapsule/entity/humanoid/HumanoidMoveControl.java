package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

public class HumanoidMoveControl extends MoveControl {

    public HumanoidMoveControl(Mob mob) {
        super(mob);
    }
}
