package com.curiats.returncapsule.mixin;

import com.curiats.returncapsule.util.TickingContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTickContextMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), require = 1)
    private void returncapsule$enterContext(Entity entity, CallbackInfo info) {
        TickingContext.enter(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"), require = 1)
    private void returncapsule$exitContext(Entity entity, CallbackInfo info) {
        TickingContext.exit();
    }
}
