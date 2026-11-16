package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class BaseHumanoid extends NeutralHumanoid {
    public BaseHumanoid(EntityType<? extends NeutralHumanoid> type, Level level) {
        super(type, level, HumanoidProfile.BASE);
    }
}
