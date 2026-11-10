package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BeaconRecords extends SavedData {
    private static final String DATA_NAME = "returncapsule_beacons";
    private static final String OWNER = "Owner";
    private static final String DIMENSION = "Dimension";
    private static final String CHUNK_X = "ChunkX";
    private static final String CHUNK_Z = "ChunkZ";
    private static final String ENTRIES = "Entries";

    private final Map<UUID, Set<ChunkKey>> records = new HashMap<>();

    public static BeaconRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(BeaconRecords::load, BeaconRecords::new, DATA_NAME);
    }

    public void record(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {
        this.records.computeIfAbsent(owner, ignored -> new HashSet<>())
                .add(new ChunkKey(dimension, pos.getX() >> 4, pos.getZ() >> 4));
        this.setDirty();
    }

    public void removeChunk(ResourceKey<Level> dimension, BlockPos pos) {
        ChunkKey key = new ChunkKey(dimension, pos.getX() >> 4, pos.getZ() >> 4);
        boolean changed = false;
        for (Set<ChunkKey> set : this.records.values()) {
            changed |= set.remove(key);
        }
        if (changed) {
            this.setDirty();
        }
    }

    public boolean hasActiveBeacon(MinecraftServer server, UUID owner) {
        Set<ChunkKey> set = this.records.get(owner);
        if (set == null) {
            return false;
        }
        for (ChunkKey key : set) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            LevelChunk chunk = level.getChunk(key.x(), key.z());
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof BeaconBlockEntity beacon && !beacon.getBeamSections().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        if (!event.getPlacedBlock().is(Blocks.BEACON) || player.getServer() == null) {
            return;
        }
        get(player.getServer()).record(player.getUUID(), player.level().dimension(), event.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
                || player.getServer() == null || !event.getState().is(Blocks.BEACON)) {
            return;
        }
        get(player.getServer()).removeChunk(player.level().dimension(), event.getPos());
    }

    private static BeaconRecords load(CompoundTag tag) {
        BeaconRecords data = new BeaconRecords();
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID(OWNER)) {
                continue;
            }
            UUID owner = entry.getUUID(OWNER);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    new ResourceLocation(entry.getString(DIMENSION)));
            data.records.computeIfAbsent(owner, ignored -> new HashSet<>())
                    .add(new ChunkKey(dimension, entry.getInt(CHUNK_X), entry.getInt(CHUNK_Z)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Set<ChunkKey>> owner : this.records.entrySet()) {
            for (ChunkKey key : owner.getValue()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID(OWNER, owner.getKey());
                entry.putString(DIMENSION, key.dimension().location().toString());
                entry.putInt(CHUNK_X, key.x());
                entry.putInt(CHUNK_Z, key.z());
                entries.add(entry);
            }
        }
        tag.put(ENTRIES, entries);
        return tag;
    }

    private record ChunkKey(ResourceKey<Level> dimension, int x, int z) {
    }

    private BeaconRecords() {
    }
}
