package com.curiats.returncapsule.block;

import com.curiats.returncapsule.compat.BladeLoot;
import com.curiats.returncapsule.compat.LootLayout;
import com.curiats.returncapsule.compat.TaCZIntegration;
import com.curiats.returncapsule.compat.TransportLoot;
import com.curiats.returncapsule.loot.BulkCargo;
import com.curiats.returncapsule.loot.Supplies;
import com.curiats.returncapsule.loot.TieredLoot;
import com.curiats.returncapsule.progress.SummonTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

final class CapsuleLoot {
    enum Phase {
        UNASSIGNED,
        TABLE,
        FILLED,
        READY
    }

    private static final String TAG_PHASE = "LootPhase";
    private static final String TAG_ASSIGNED = "LootAssigned";
    private static final String TAG_TACZ = "TaCZGenerated";
    private static final String TAG_GENERATED = "LootGenerated";

    private final CapsuleBlockEntity owner;
    private Phase phase = Phase.UNASSIGNED;
    private boolean taczItems;

    CapsuleLoot(CapsuleBlockEntity owner) {
        this.owner = owner;
    }

    Phase phase() {
        return this.phase;
    }

    void ensureAssigned() {
        if (this.phase == Phase.FILLED || this.phase == Phase.READY) {
            return;
        }
        if (!(this.owner.getLevel() instanceof ServerLevel server)) {
            return;
        }
        
        
        
        
        RandomSource random = server.random;
        SummonTier tier = this.owner.getTier();
        if (this.phase == Phase.UNASSIGNED) {
            this.owner.assignLootTable(tier.lootTable(), random.nextLong());
            this.phase = Phase.TABLE;
        }
    }

    void prepareForAccess(Player player) {
        if (this.phase == Phase.READY) {
            return;
        }
        this.ensureAssigned();
        if (this.phase == Phase.TABLE) {
            this.owner.unpackLootTableFor(player);
            this.phase = Phase.FILLED;
        }
        if (this.phase == Phase.FILLED) {
            TieredLoot.fillModItems(this.owner, this.owner.getTier(), player.getRandom());
            Supplies.fill(this.owner, this.owner.getTier(), player.getRandom());
            BladeLoot.fill(this.owner, this.owner.getTier(), player.getRandom());
            BulkCargo.fill(this.owner, this.owner.getTier(), player.getRandom());
            if (TieredLoot.allowsGunLoot(this.owner.getTier()) && TaCZIntegration.isLoaded()) {
                this.taczItems = TaCZIntegration.fill(this.owner, player.getRandom(), this.owner.getTier());
            }
            LootLayout.spread(this.owner, player.getRandom());
            com.curiats.returncapsule.util.LootLimits.trimMusicDiscs(this.owner, 3);
            this.owner.clearLootTable();
            this.phase = Phase.READY;
            this.owner.setChanged();
        }
    }

    void markGenerated() {
        this.phase = Phase.READY;
        this.owner.clearLootTable();
        this.owner.setChanged();
    }

    void onTableAssigned() {
        this.phase = Phase.TABLE;
    }

    void save(CompoundTag tag) {
        tag.putString(TAG_PHASE, this.phase.name());
        tag.putBoolean(TAG_ASSIGNED, this.phase != Phase.UNASSIGNED);
        tag.putBoolean(TAG_TACZ, this.taczItems);
        tag.putBoolean(TAG_GENERATED, this.phase == Phase.READY);
    }

    void load(CompoundTag tag, boolean hasLootTable) {
        this.taczItems = tag.getBoolean(TAG_TACZ);
        if (tag.contains(TAG_PHASE)) {
            try {
                this.phase = Phase.valueOf(tag.getString(TAG_PHASE));
                return;
            } catch (IllegalArgumentException ignored) {
                
            }
        }
        if (tag.getBoolean(TAG_GENERATED)) {
            this.phase = Phase.READY;
        } else if (this.taczItems) {
            this.phase = Phase.FILLED;
        } else if (tag.getBoolean(TAG_ASSIGNED) || hasLootTable) {
            this.phase = Phase.TABLE;
        } else {
            this.phase = Phase.UNASSIGNED;
        }
    }
}
