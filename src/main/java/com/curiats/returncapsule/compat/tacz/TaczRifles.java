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

    /** Every installed gun whose TaCZ type is one of {@link #SERVICE_TYPES}. Cached: this is polled per tick. */
    public static List<GunInfo> serviceGuns() {
        List<GunInfo> local = serviceCache;
        if (local != null) {
            return local;
        }
        List<GunInfo> built = new ArrayList<>();
        for (GunInfo gun : TaczCatalog.snapshot().guns()) {
            if (SERVICE_TYPES.contains(gun.type().toLowerCase(Locale.ROOT))) {
                built.add(gun);
            }
        }
        local = List.copyOf(built);
        serviceCache = local;
        return local;
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
    public static ItemStack randomServiceGun(RandomSource random) {
        List<GunInfo> pool = serviceGuns();
        if (pool.isEmpty()) {
            warnEmptyOnce();
            return ItemStack.EMPTY;
        }
        ItemStack gun = TaczItems.mobGun(pool.get(random.nextInt(pool.size())), MOB_MAGAZINE, MOB_DUMMY_AMMO);
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
