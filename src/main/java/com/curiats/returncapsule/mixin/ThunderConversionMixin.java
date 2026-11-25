package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.util.AnchorField;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lightning conversions (pig, villager, mooshroom) are blocked inside the anchor field. */
@Mixin({Pig.class, Villager.class, MushroomCow.class})
public abstract class ThunderConversionMixin {

    @Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true, require = 0)
    private void returncapsule$blockThunderConversion(ServerLevel level, LightningBolt bolt, CallbackInfo info) {
        Mob self = (Mob) (Object) this;
        if (AnchorField.covers(level, self.position())) {
            info.cancel();
        }
    }
}
