package com.curiats.returncapsule.compat.tacz;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only helpers for the neutral humanoids: is this stack a TaCZ gun, which kind is it (including guns added by
 * gun packs), what range is that kind useful at, and a supply of usable guns.
 * <p>
 * Gun packs do not register their own items: they contribute {@code GunId} data to the gun item, so every kind test
 * goes through TaCZ's own {@code IGun} marker and the data driven gun index, never an item id.
 */
public final class TaczRifles {
    private static final String TAG_GUN_ID = "GunId";
    private static final String IGUN_CLASS = "com.tacz.guns.api.item.IGun";
    private static final int MOB_MAGAZINE = 30;
    private static final int MOB_DUMMY_AMMO = 9_999;

    /**
     * Gun kinds a humanoid is issued. Rifles are the baseline; snipers and marksman rifles are included so a
     * long gun actually reaches further than a rifle's ~60 blocks. Drop an entry here to stop issuing that kind.
     */
    private static final Set<String> SERVICE_TYPES = Set.of("rifle", "sniper", "dmr");

    private static volatile boolean resolved;
    private static volatile boolean warnedEmpty;
    private static Method gunOrNull;
    private static volatile Method getRpm;
    private static volatile List<GunInfo> serviceCache;
    private static volatile Map<ResourceLocation, String> typeById;

    /** Drops the cached gun lists when the gun index is rebuilt. */
    public static void invalidate() {
        serviceCache = null;
        typeById = null;
    }

    public static boolean isGun(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Method marker = gunOrNull();
        if (marker != null) {
            try {
                return marker.invoke(null, stack) != null;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall through to the item id check below.
            }
        }
        return TaczItems.GUN_ITEM.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Gun id of a stack, or {@code null} when it is not a gun. */
    public static ResourceLocation gunId(ItemStack stack) {
        if (!isGun(stack)) {
            return null;
        }
        if (!stack.hasTag() || !stack.getOrCreateTag().contains(TAG_GUN_ID)) {
            return null;
        }
        try {
            return new ResourceLocation(stack.getOrCreateTag().getString(TAG_GUN_ID));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** TaCZ type string of the held gun ("rifle", "sniper", ...), or {@code ""} when unknown. */
    public static String gunType(ItemStack stack) {
        ResourceLocation id = gunId(stack);
        return id == null ? "" : types().getOrDefault(id, "");
    }

    /** Whether this gun is one the humanoids are willing to carry. */
    public static boolean isServiceGun(ItemStack stack) {
        return SERVICE_TYPES.contains(gunType(stack));
    }

    /**
     * How far this gun type is practically useful. Firing itself is not gated by this — a humanoid shoots at
     * whatever its target engagement range allows (see {@code HumanoidCombatRules}) — but it is the distance a
     * shooter stops sprinting at when it has to walk somewhere to get a line of sight.
     */
    public static double effectiveRange(String type) {
        return switch (type == null ? "" : type) {
            case "sniper" -> 180.0D;
            case "dmr" -> 100.0D;
            case "mg" -> 80.0D;
            case "rifle" -> 60.0D;
            case "rpg" -> 60.0D;
            case "pistol" -> 40.0D;
            case "smg" -> 30.0D;
            case "shotgun" -> 20.0D;
            default -> 60.0D;
        };
    }

    /**
     * Every installed gun whose TaCZ type is one of {@link #SERVICE_TYPES}. Cached: this is polled per tick.
     * <p>
     * Guns are additionally restricted to those the client has resource data for. Firing works without a model, so a
     * gun whose client index never loaded shoots fine while being invisible in hand; filtering against the client set is
     * the only reliable way to avoid handing those out, because it needs no per-gun testing and covers every pack.
     */
    public static List<GunInfo> serviceGuns() {
        List<GunInfo> local = serviceCache;
        if (local != null) {
            return local;
        }
        Set<ResourceLocation> renderable = renderableGuns();
        if (renderable.isEmpty()) {
            // The client index is not loaded yet on the first call. Do not cache an unfiltered list, or every gun after
            // this point is handed out without a renderability check.
            List<GunInfo> unfiltered = new ArrayList<>();
            for (GunInfo gun : TaczCatalog.snapshot().guns()) {
                if (SERVICE_TYPES.contains(gun.type().toLowerCase(Locale.ROOT))) {
                    unfiltered.add(gun);
                }
            }
            return List.copyOf(unfiltered);
        }
        List<GunInfo> built = new ArrayList<>();
        for (GunInfo gun : TaczCatalog.snapshot().guns()) {
            if (!SERVICE_TYPES.contains(gun.type().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!renderable.isEmpty() && !renderable.contains(gun.id())) {
                continue;
            }
            built.add(gun);
        }
        local = List.copyOf(built);
        serviceCache = local;
        return local;
    }

    /**
     * Gun ids the client can actually draw, from TaCZ's own client index. Empty on a dedicated server (the data only
     * exists on the client), in which case no filtering happens.
     */
    private static Set<ResourceLocation> renderableGuns() {
        if (!net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            return Set.of();
        }
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object entries = api.getMethod("getAllClientGunIndex").invoke(null);
            if (!(entries instanceof Iterable<?> iterable)) {
                return Set.of();
            }
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            for (Object entry : iterable) {
                if (entry instanceof Map.Entry<?, ?> mapEntry && mapEntry.getKey() instanceof ResourceLocation id) {
                    ids.add(id);
                }
            }
            ReturnCapsuleMod.LOGGER.info("[humanoid] {} guns have client models", ids.size());
            return ids;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.debug("Client gun index unavailable: {}", exception.toString());
            return Set.of();
        }
    }

    private static Map<ResourceLocation, String> types() {
        Map<ResourceLocation, String> local = typeById;
        if (local != null) {
            return local;
        }
        Map<ResourceLocation, String> built = new HashMap<>();
        for (GunInfo gun : TaczCatalog.snapshot().guns()) {
            built.put(gun.id(), gun.type().toLowerCase(Locale.ROOT));
        }
        local = Map.copyOf(built);
        typeById = local;
        return local;
    }

    /**
     * A gun for a neutral humanoid: picked from the issued kinds, carrying TaCZ dummy ammo. TaCZ consumes real
     * magazine rounds for non-player shooters and would otherwise need an inventory to reload from, which a mob has not.
     */
    /**
     * A long range gun for the humanoid's second slot: a sniper or a marksman rifle.
     * <p>
     * A rifle's effective range is about sixty blocks, so past that the humanoid needs something else or it is shooting
     * into falloff. Same construction path as the service gun, attachments included.
     */
    public static ItemStack randomLongGun(RandomSource random) {
        List<GunInfo> pool = new java.util.ArrayList<>();
        for (GunInfo gun : serviceGuns()) {
            String type = gun.type().toLowerCase(Locale.ROOT);
            if ("sniper".equals(type) || "dmr".equals(type)) {
                pool.add(gun);
            }
        }
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack gun = TaczItems.mobGun(pool.get(random.nextInt(pool.size())), MOB_MAGAZINE, MOB_DUMMY_AMMO);
        TaczItems.randomizeAttachments(gun, random);
        return gun;
    }

    public static ItemStack randomServiceGun(RandomSource random) {
        List<GunInfo> pool = serviceGuns();
        if (pool.isEmpty()) {
            warnEmptyOnce();
            return ItemStack.EMPTY;
        }
        // The rifle slot wants an actual rifle. The service pool also contains snipers and marksman rifles, and those are
        // almost all single shot by design — pulling from the whole pool is why the rifle slot so often ended up holding
        // something that fires one round per trigger pull.
        List<GunInfo> rifles = new java.util.ArrayList<>();
        for (GunInfo gun : pool) {
            if ("rifle".equalsIgnoreCase(gun.type())) {
                rifles.add(gun);
            }
        }
        List<GunInfo> byType = rifles.isEmpty() ? pool : rifles;

        // Then prefer full auto within that: burst and semi guns still fire one round per trigger pull.
        List<GunInfo> automatic = new java.util.ArrayList<>();
        for (GunInfo gun : byType) {
            if ("AUTO".equalsIgnoreCase(gun.fireMode())) {
                automatic.add(gun);
            }
        }
        List<GunInfo> chosenPool = automatic.isEmpty() ? byType : automatic;
        ItemStack gun = TaczItems.mobGun(chosenPool.get(random.nextInt(chosenPool.size())),
                MOB_MAGAZINE, MOB_DUMMY_AMMO);
        TaczItems.randomizeAttachments(gun, random);
        return gun;
    }

    /**
     * Speaks up once when no gun could be issued. Without this the only symptom is a humanoid that never shoots, which
     * gives no clue whether the gun index failed to build or the type filter matched nothing.
     */
    private static void warnEmptyOnce() {
        if (warnedEmpty) {
            return;
        }
        warnedEmpty = true;
        ReturnCapsuleMod.LOGGER.warn("[humanoid] no usable TaCZ long gun: {} guns indexed, {} matched {}; "
                        + "humanoids will spawn without a gun",
                TaczCatalog.snapshot().guns().size(), serviceGuns().size(), SERVICE_TYPES);
    }

    /**
     * Makes a gun usable by a mob: ensures TaCZ dummy ammo is present and the magazine is not empty.
     * <p>
     * Guns that come from our own spawn pool already carry this, but a gun handed over by a player does not — and a mob
     * has no inventory for TaCZ to reload from, so without this it fires its magazine once and then refuses forever.
     */
    public static void prepareForMob(ItemStack gun) {
        if (!isGun(gun)) {
            return;
        }
        var tag = gun.getOrCreateTag();
        if (!tag.contains("DummyAmmo") || tag.getInt("DummyAmmo") <= 0) {
            tag.putInt("DummyAmmo", MOB_DUMMY_AMMO);
            tag.putInt("MaxDummyAmmo", MOB_DUMMY_AMMO);
        }
        if (tag.getInt("GunCurrentAmmoCount") <= 0) {
            tag.putInt("GunCurrentAmmoCount", MOB_MAGAZINE);
            tag.putBoolean("HasBulletInBarrel", true);
        }
    }

    /** Rounds per minute of the held gun, or {@code 0} when unknown. Used to pace the AI's trigger. */
    public static int rpm(ItemStack stack) {
        if (!isGun(stack) || gunOrNull() == null) {
            return 0;
        }
        try {
            Object gun = gunOrNull().invoke(null, stack);
            if (gun == null) {
                return 0;
            }
            if (getRpm == null) {
                getRpm = Class.forName(IGUN_CLASS).getMethod("getRPM", ItemStack.class);
            }
            Object value = getRpm.invoke(gun, stack);
            return value instanceof Integer rounds ? rounds : 0;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return 0;
        }
    }

    private static Method gunOrNull() {
        if (resolved) {
            return gunOrNull;
        }
        synchronized (TaczRifles.class) {
            if (!resolved) {
                resolved = true;
                try {
                    gunOrNull = Class.forName(IGUN_CLASS).getMethod("getIGunOrNull", ItemStack.class);
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    gunOrNull = null;
                }
            }
            return gunOrNull;
        }
    }

    private TaczRifles() {
    }
}
