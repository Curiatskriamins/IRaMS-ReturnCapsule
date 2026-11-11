package com.curiats.returncapsule.compat.tacz;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaczCatalog {
    private static volatile Snapshot snapshot;

    static Snapshot snapshot() {
        Snapshot local = snapshot;
        if (local == null) {
            synchronized (TaczCatalog.class) {
                local = snapshot;
                if (local == null) {
                    local = build();
                    
                    
                    
                    if (!local.isEmpty()) {
                        snapshot = local;
                    }
                }
            }
        }
        return local;
    }

    public static void invalidate() {
        snapshot = null;
    }

    private static Snapshot build() {
        if (!TaczApi.resolve()) {
            return Snapshot.EMPTY;
        }
        List<GunInfo> guns = new ArrayList<>();
        for (Map.Entry<?, ?> entry : TaczApi.gunIndex()) {
            if (!(entry.getKey() instanceof ResourceLocation id) || entry.getValue() == null) {
                continue;
            }
            Object index = entry.getValue();
            Object gunData = TaczApi.readGunData(index);
            ResourceLocation ammoId = gunData == null ? null : TaczApi.readAmmoId(gunData);
            String fireMode = gunData == null ? "SEMI" : TaczApi.readFireMode(gunData);
            guns.add(new GunInfo(id, TaczApi.readType(index), ammoId, fireMode));
        }

        Map<String, List<GunInfo>> gunsByPack = new LinkedHashMap<>();
        for (GunInfo gun : guns) {
            gunsByPack.computeIfAbsent(gun.id().getNamespace(), key -> new ArrayList<>()).add(gun);
        }

        Map<String, List<ResourceLocation>> attachmentsByPack = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : TaczApi.attachmentIndex()) {
            if (entry.getKey() instanceof ResourceLocation id) {
                attachmentsByPack.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>()).add(id);
            }
        }

        Snapshot built = new Snapshot(
                Collections.unmodifiableList(guns),
                freeze(gunsByPack),
                freeze(attachmentsByPack));
        ReturnCapsuleMod.LOGGER.info("Indexed {} TaCZ guns across {} packs and {} attachment packs",
                built.guns().size(), built.gunsByPack().size(), built.attachmentsByPack().size());
        return built;
    }

    private static <T> Map<String, List<T>> freeze(Map<String, List<T>> source) {
        Map<String, List<T>> frozen = new LinkedHashMap<>(source.size());
        source.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(frozen);
    }

    static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(List.of(), Map.of(), Map.of());

        private final List<GunInfo> guns;
        private final Map<String, List<GunInfo>> gunsByPack;
        private final Map<String, List<ResourceLocation>> attachmentsByPack;

        Snapshot(List<GunInfo> guns, Map<String, List<GunInfo>> gunsByPack,
                 Map<String, List<ResourceLocation>> attachmentsByPack) {
            this.guns = guns;
            this.gunsByPack = gunsByPack;
            this.attachmentsByPack = attachmentsByPack;
        }

        List<GunInfo> guns() {
            return this.guns;
        }

        Map<String, List<GunInfo>> gunsByPack() {
            return this.gunsByPack;
        }

        Map<String, List<ResourceLocation>> attachmentsByPack() {
            return this.attachmentsByPack;
        }

        boolean isEmpty() {
            return this.guns.isEmpty();
        }
    }

    private TaczCatalog() {
    }
}
