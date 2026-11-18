package com.curiats.returncapsule.entity.humanoid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HumanoidSight {

    private static final int MAX_PASSES = 6;

    private HumanoidSight() {
    }

    public static boolean canSee(Level level, Entity viewer, Vec3 from, Vec3 to) {
        Vec3 start = from;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            BlockHitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, viewer));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (!transparent(level, hit.getBlockPos())) {
                return false;
            }
            Vec3 direction = to.subtract(start);
            if (direction.lengthSqr() < 1.0E-4D) {
                return true;
            }
            start = hit.getLocation().add(direction.normalize().scale(0.35D));
            if (start.distanceToSqr(to) < 0.25D) {
                return true;
            }
        }
        return false;
    }

    public static boolean canSee(Level level, Entity viewer, Entity target) {
        return canSee(level, viewer, viewer.getEyePosition(), target.getEyePosition());
    }

    private static boolean transparent(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        String path = id == null ? "" : id.getPath();
        if (path.contains("web") || path.contains("vine") || path.contains("cobweb")) {
            return true;
        }
        return state.getCollisionShape(level, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty();
    }
}
