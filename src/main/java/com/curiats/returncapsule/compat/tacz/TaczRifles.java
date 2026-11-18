package com.curiats.returncapsule.compat.tacz;

import com.curiats.returncapsule.ReturnCapsuleMod;
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
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings({"removal"})
public final class TaczRifles {
    private static final String TAG_GUN_ID = "GunId";
    private static final String IGUN_CLASS = "com.tacz.guns.api.item.IGun";
    private static final int MOB_MAGAZINE = 30;
    private static final int MOB_DUMMY_AMMO = 9_999;

    private static final Set<String> SERVICE_TYPES = Set.of("rifle", "sniper", "dmr", "mg", "smg", "rpg");

    private static final List<String> EXCLUDED_GUN_KEYWORDS =
            List.of("m134", "minigun", "gatling", "chain_gun", "chain gun");

    private static final Set<String> LONG_SLOT_TYPES = Set.of("sniper", "dmr", "rpg");

    private static final double MIN_SERVICE_RANGE = 16.0D;

    private static volatile boolean resolved;
    private static volatile boolean warnedEmpty;
    private static Method gunOrNull;
    private static volatile Method getRpm;
    private static volatile List<GunInfo> serviceCache;
    private static volatile Map<ResourceLocation, String> typeById;

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

            }
        }
        return TaczItems.GUN_ITEM.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

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

    public static String gunType(ItemStack stack) {
        ResourceLocation id = gunId(stack);
        return id == null ? "" : types().getOrDefault(id, "");
    }

    public static boolean isServiceGun(ItemStack stack) {
        if (!SERVICE_TYPES.contains(gunType(stack))) {
            return false;
        }
        return !isExcluded(gunId(stack));
    }

    public static boolean isMeleeGun(ItemStack stack) {
        ResourceLocation id = gunId(stack);
        if (id == null) {
            return false;
        }
        for (GunInfo gun : TaczCatalog.snapshot().guns()) {
            if (gun.id().equals(id)) {
                return gun.range() > 0.0D && gun.range() < MIN_SERVICE_RANGE;
            }
        }
        return false;
    }

    public static boolean isLongSlot(String type) {
        return type != null && LONG_SLOT_TYPES.contains(type.toLowerCase(Locale.ROOT));
    }


    private static boolean shortRange(GunInfo gun) {
        return gun.range() > 0.0D && gun.range() < MIN_SERVICE_RANGE;
    }

    private static boolean isExcluded(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        for (String keyword : EXCLUDED_GUN_KEYWORDS) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

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

    public static List<GunInfo> serviceGuns() {
        List<GunInfo> local = serviceCache;
        if (local != null) {
            return local;
        }
        Set<ResourceLocation> renderable = renderableGuns();
        if (renderable.isEmpty()) {

            List<GunInfo> unfiltered = new ArrayList<>();
            for (GunInfo gun : TaczCatalog.snapshot().guns()) {
                if (SERVICE_TYPES.contains(gun.type().toLowerCase(Locale.ROOT))
                        && !isExcluded(gun.id()) && !shortRange(gun)) {
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
            if (isExcluded(gun.id()) || shortRange(gun)) {
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

    public static ItemStack randomLongGun(RandomSource random) {
        List<GunInfo> pool = new java.util.ArrayList<>();
        for (GunInfo gun : serviceGuns()) {
            String type = gun.type().toLowerCase(Locale.ROOT);
            if (isLongSlot(type)) {
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

        List<GunInfo> rifles = new java.util.ArrayList<>();
        for (GunInfo gun : pool) {
            if (!isLongSlot(gun.type())) {
                rifles.add(gun);
            }
        }
        List<GunInfo> byType = rifles.isEmpty() ? pool : rifles;

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

    private static void warnEmptyOnce() {
        if (warnedEmpty) {
            return;
        }
        warnedEmpty = true;
        ReturnCapsuleMod.LOGGER.warn("[humanoid] no usable TaCZ long gun: {} guns indexed, {} matched {}; "
                        + "humanoids will spawn without a gun",
                TaczCatalog.snapshot().guns().size(), serviceGuns().size(), SERVICE_TYPES);
    }

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
