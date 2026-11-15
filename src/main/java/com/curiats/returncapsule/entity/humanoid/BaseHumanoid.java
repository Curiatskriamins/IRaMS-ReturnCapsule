package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The concrete entity class behind {@code returncapsule:base_humanoid}.
 * <p>
 * It exists purely because MoBends resolves an entity type from a class by name: it compares
 * {@code class.getSimpleName()} against the registry path with underscores removed, so {@code base_humanoid} only
 * matches a class called {@code BaseHumanoid}. Registering {@link NeutralHumanoid} directly made that lookup fail with
 * "Unable to find an EntityType for ..." and the addon registration threw.
 * <p>
 * Behaviour lives in {@link NeutralHumanoid}; this subclass only pins the profile.
 */
public class BaseHumanoid extends NeutralHumanoid {
    public BaseHumanoid(EntityType<? extends NeutralHumanoid> type, Level level) {
        super(type, level, HumanoidProfile.BASE);
    }
}
