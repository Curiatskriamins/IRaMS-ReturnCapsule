package com.curiats.returncapsule.entity;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.compat.ShockManager;
import com.curiats.returncapsule.entity.fall.CapsuleKind;
import com.curiats.returncapsule.entity.fall.ShakeBurst;
import com.curiats.returncapsule.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * Landing template shared by every capsule.
 *
 * <p>{@link #onLanded} is final: it resolves the landing spot, runs the impact effects, applies the shock only when the
 * kind says so, lets the subclass place its cargo and run its own after-land hook. Subclasses override the hooks, never
 * the order of the sequence.</p>
 *
 * <p>Draft change: the template keeps the original order of the two pre-refactor implementations — impact effects, then
 * the shock, then the world edits, then the cargo. {@code beforeShock} exists for the transport capsule, which used to
 * eject its passengers before the shock, and {@code afterShock} for the supply capsule, which used to clear and scatter
 * after the shock. The shock origin is back at {@code y + 1.0}, which is what both old implementations used.</p>
 */
public abstract class AbstractCapsuleEntity extends FallingCapsuleEntity {

    protected final CapsuleInventory inventory;

    protected AbstractCapsuleEntity(EntityType<?> type, Level level) {
        super(type, level);
        int slots = capsuleKind().slots() > 0 ? capsuleKind().slots() : Config.containerSlots();
        this.inventory = new CapsuleInventory(slots, player -> player.distanceToSqr(this) < 64.0D);
    }

    public abstract CapsuleKind capsuleKind();

    public CapsuleInventory inventory() {
        return this.inventory;
    }

    @Override
    protected final void onLanded(ServerLevel server) {
        if (this.impacted || this.anchor == null) {
            return;
        }
        this.impacted = true;
        this.anchor = resolveLanding(server);
        if (this.anchor == null) {
            this.discard();
            return;
        }
        BlockPos landing = this.anchor;
        Vec3 center = new Vec3(landing.getX() + 0.5D, landing.getY() + 1.0D, landing.getZ() + 0.5D);
        this.impactEffects(server, landing);
        Set<UUID> excluded = this.shockExclusions(server);
        this.beforeShock(server, landing);
        if (capsuleKind().trueDamage()) {
            ShockManager.impact(server, center, Config.IMPACT_HALF_SIZE.get(), this, excluded);
        }
        this.afterShock(server, landing);
        this.afterImpact(server, landing);
        this.placeCargo(server, landing);
        this.discard();
    }

    /** Subclasses that move the landing spot before placement override this. */
    protected BlockPos resolveLanding(ServerLevel server) {
        return this.anchor;
    }

    /** Common impact sound and camera shake; subclasses extend it with their own effects. */
    protected void impactEffects(ServerLevel server, BlockPos landing) {
        double x = landing.getX() + 0.5D;
        double y = landing.getY() + 1.0D;
        double z = landing.getZ() + 0.5D;
        server.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                Config.IMPACT_SOUND_VOLUME.get().floatValue(),
                Config.IMPACT_SOUND_PITCH.get().floatValue());
        server.playSound(null, x, y, z, ModSounds.CAPSULE_IMPACT.get(), SoundSource.BLOCKS,
                Config.IMPACT_THUD_VOLUME.get().floatValue(),
                Config.IMPACT_THUD_PITCH.get().floatValue());
        if (Config.SCREEN_SHAKE.get()) {
            ShakeBurst.around(server, x, y, z, Config.SHAKE_RADIUS.get(), 25, 0);
        }
    }

    /** Runs after the impact effects and before the shock; the transport capsule ejects its passengers here. */
    protected void beforeShock(ServerLevel server, BlockPos landing) {
    }

    /** Runs after the shock and before the cargo is placed; the supply capsule clears the area here. */
    protected void afterShock(ServerLevel server, BlockPos landing) {
    }

    protected void afterImpact(ServerLevel server, BlockPos landing) {
    }

    protected abstract void placeCargo(ServerLevel server, BlockPos landing);

    protected Set<UUID> shockExclusions(ServerLevel server) {
        return Set.of();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.inventory.save(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.inventory.load(tag);
    }
}
