package com.curiats.returncapsule.entity;

import com.curiats.returncapsule.Config;
import com.curiats.returncapsule.entity.fall.CapsuleKind;
import com.curiats.returncapsule.entity.fall.CapsuleLanding;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.curiats.returncapsule.progress.SummonTier;
import com.curiats.returncapsule.registry.ModEntities;
import com.curiats.returncapsule.util.BoundLoadout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The capsule summoned away from an obsidian platform: no true damage, one owned humanoid, five slot cargo.
 */
public class MannedCapsuleEntity extends AbstractCapsuleEntity {

    private static final String TAG_OWNER = "Owner";

    @Nullable
    private UUID ownerUUID;

    public MannedCapsuleEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public CapsuleKind capsuleKind() {
        return CapsuleKind.MANNED;
    }

    public static boolean spawn(ServerLevel server, BlockPos landing, ServerPlayer owner) {
        MannedCapsuleEntity entity = new MannedCapsuleEntity(ModEntities.MANNED_CAPSULE.get(), server);
        entity.beginFall(landing, owner, Config.spawnHeight());
        entity.ownerUUID = owner.getUUID();
        ItemStack[] set = BoundLoadout.grant(owner);
        if (set != null) {
            for (int slot = 0; slot < set.length && slot < entity.inventory.getContainerSize(); slot++) {
                entity.inventory.setItem(slot, set[slot]);
            }
            // Slot six carries the storage anchor, so the first capsule hands out the whole kit.
            int anchorSlot = set.length;
            if (anchorSlot < entity.inventory.getContainerSize()) {
                entity.inventory.setItem(anchorSlot,
                        new ItemStack(com.curiats.returncapsule.registry.ModItems.STANDARD_STORAGE_ANCHOR.get()));
            }
        }
        return server.addFreshEntity(entity);
    }

    @Override
    protected double fallSpeed(double currentVelocity) {
        return -Config.TERMINAL_VELOCITY.get();
    }

    /**
     * Draft change: the old spot search could only ever pick the top centre of the 5x5 shell, which is a solid part
     * block, so the humanoid was spawned inside the capsule. Search a ring outside the footprint instead and only fall
     * back to the space above the shell.
     */
    private void spawnHumanoid(ServerLevel server, BlockPos landing) {
        NeutralHumanoid humanoid = ModEntities.BASE_HUMANOID.get().create(server);
        if (humanoid == null) {
            return;
        }
        BlockPos spot = findFreeSpot(server, landing);
        humanoid.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, this.getYRot(), 0.0F);
        humanoid.setOwnerUUID(this.ownerUUID);
        humanoid.setPersistenceRequired();
        server.addFreshEntity(humanoid);
    }

    private static BlockPos findFreeSpot(ServerLevel server, BlockPos landing) {
        int inner = CapsuleKind.MANNED.radius() + 1;
        for (int ring = inner; ring <= inner + 2; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    BlockPos candidate = landing.offset(dx, 0, dz);
                    if (hasStandingRoom(server, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        // Nothing around is free: at least use the open space above the shell instead of its solid top layer.
        return landing.above(CapsuleKind.MANNED.height());
    }

    private static boolean hasStandingRoom(ServerLevel server, BlockPos pos) {
        return server.getBlockState(pos).getCollisionShape(server, pos).isEmpty()
                && server.getBlockState(pos.above()).getCollisionShape(server, pos.above()).isEmpty();
    }

    @Override
    protected void placeCargo(ServerLevel server, BlockPos landing) {
        // Draft change: CapsuleLanding.land now reports a failed placement, so the cargo is not silently deleted.
        if (!CapsuleLanding.land(server, landing, this.landingFacing, CapsuleKind.MANNED,
                this.inventory, this.tier)) {
            Containers.dropContents(server, landing, this.inventory);
            this.inventory.clearContent();
            return;
        }
        this.spawnHumanoid(server, landing);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUUID != null) {
            tag.putUUID(TAG_OWNER, this.ownerUUID);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(TAG_OWNER)) {
            this.ownerUUID = tag.getUUID(TAG_OWNER);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(this.tier.transportKey());
    }
}
