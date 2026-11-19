package com.curiats.returncapsule.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Permanent max health strip used by the standard issue blade.
 *
 * <p>Each hit takes a percentage of the target's <em>original</em> max health out of the max health pool, up to a
 * configurable cap. Once the cap is reached the fraction stops being a strip and is handed back as true damage instead,
 * so a high health target still loses ground without the max health pool collapsing.</p>
 *
 * <p>The original max health and the amount already stripped live in the entity's persistent data, so a save and reload
 * does not reset the cap, and a single attribute modifier always carries the running total.</p>
 */
public final class MaxHealthStrip {

    private static final UUID MODIFIER_ID = UUID.fromString("6a4c6c62-1e0c-4d1e-9c5a-2f0f7a1b3d55");

    private static final String NAME = "returncapsule_health_strip";

    private static final String TAG_BASE = "returncapsule_base_max_health";

    private static final String TAG_STRIPPED = "returncapsule_stripped_health";

    /** @param stripped max health actually removed, {@code excess} the part left for true damage */
    public record Result(float stripped, float excess) {
    }

    public static Result apply(LivingEntity target, float fraction, float capFraction) {
        CompoundTag data = target.getPersistentData();
        float base = data.contains(TAG_BASE) ? data.getFloat(TAG_BASE) : (float) target.getMaxHealth();
        if (!data.contains(TAG_BASE)) {
            data.putFloat(TAG_BASE, base);
        }
        float wanted = base * fraction;
        float already = data.getFloat(TAG_STRIPPED);
        float cap = base * capFraction;
        float stripped = Math.min(wanted, Math.max(0.0F, cap - already));
        float excess = wanted - stripped;
        if (stripped <= 0.0F) {
            return new Result(0.0F, excess);
        }
        float total = already + stripped;
        data.putFloat(TAG_STRIPPED, total);
        AttributeInstance attribute = target.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(MODIFIER_ID);
            attribute.addPermanentModifier(new AttributeModifier(MODIFIER_ID, NAME, -total,
                    AttributeModifier.Operation.ADDITION));
        }
        if (target.getHealth() > target.getMaxHealth()) {
            target.setHealth(target.getMaxHealth());
        }
        return new Result(stripped, excess);
    }

    private MaxHealthStrip() {
    }
}
