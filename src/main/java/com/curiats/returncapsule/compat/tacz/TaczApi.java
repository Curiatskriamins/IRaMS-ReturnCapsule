package com.curiats.returncapsule.compat.tacz;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class TaczApi {
    private static final String API_CLASS = "com.tacz.guns.api.TimelessAPI";
    private static final String GUN_INDEX = "getAllCommonGunIndex";
    private static final String ATTACHMENT_INDEX = "getAllCommonAttachmentIndex";
    static final String ATTACHMENT_TYPE_CLASS = "com.tacz.guns.api.item.attachment.AttachmentType";

    private static volatile boolean resolved;
    private static volatile boolean available;
    private static Method gunIndexMethod;
    private static Method attachmentIndexMethod;

    private static final ClassValue<GunAccessors> GUN_ACCESSORS = new ClassValue<>() {
        @Override
        protected GunAccessors computeValue(Class<?> type) {
            try {
                return new GunAccessors(type.getMethod("getType"), type.getMethod("getGunData"));
            } catch (NoSuchMethodException | LinkageError exception) {
                return GunAccessors.UNSUPPORTED;
            }
        }
    };

    private static final ClassValue<GunDataAccessors> GUN_DATA_ACCESSORS = new ClassValue<>() {
        @Override
        protected GunDataAccessors computeValue(Class<?> type) {
            try {
                return new GunDataAccessors(type.getMethod("getAmmoId"), type.getMethod("getFireModeSet"));
            } catch (NoSuchMethodException | LinkageError exception) {
                return GunDataAccessors.UNSUPPORTED;
            }
        }
    };

    static synchronized boolean resolve() {
        if (resolved) {
            return available;
        }
        resolved = true;
        try {
            Class<?> api = Class.forName(API_CLASS);
            gunIndexMethod = api.getMethod(GUN_INDEX);
            attachmentIndexMethod = api.getMethod(ATTACHMENT_INDEX);
            available = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            ReturnCapsuleMod.LOGGER.warn("TaCZ API is not available; capsules fall back to the vanilla loot table", exception);
            available = false;
        }
        return available;
    }

    static boolean available() {
        return available;
    }

    static Collection<Map.Entry<?, ?>> gunIndex() {
        return entries(gunIndexMethod);
    }

    static Collection<Map.Entry<?, ?>> attachmentIndex() {
        return entries(attachmentIndexMethod);
    }

    private static final String IGUN_CLASS = "com.tacz.guns.api.item.IGun";
    private static final String IATTACHMENT_CLASS = "com.tacz.guns.api.item.IAttachment";

    private static volatile Method gunOrNull;
    private static volatile Method allowAttachmentType;
    private static volatile Method allowAttachment;
    private static volatile Method installAttachment;
    private static volatile Method attachmentTypeOf;

    static Object gunOrNull(net.minecraft.world.item.ItemStack stack) throws ReflectiveOperationException {
        if (gunOrNull == null) {
            Class<?> type = Class.forName(IGUN_CLASS);
            gunOrNull = type.getMethod("getIGunOrNull", net.minecraft.world.item.ItemStack.class);
            allowAttachmentType = type.getMethod("allowAttachmentType",
                    net.minecraft.world.item.ItemStack.class, Class.forName(ATTACHMENT_TYPE_CLASS));
            allowAttachment = type.getMethod("allowAttachment",
                    net.minecraft.world.item.ItemStack.class, net.minecraft.world.item.ItemStack.class);
            installAttachment = type.getMethod("installAttachment",
                    net.minecraft.world.item.ItemStack.class, net.minecraft.world.item.ItemStack.class);
        }
        return gunOrNull.invoke(null, stack);
    }

    static boolean allowAttachmentType(Object gunHandle, Object attachmentType) {
        try {
            return allowAttachmentType != null
                    && Boolean.TRUE.equals(allowAttachmentType.invoke(gunHandle, attachmentType));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    static boolean allowAttachment(Object gunHandle, net.minecraft.world.item.ItemStack attachment) {
        try {
            return allowAttachment != null && Boolean.TRUE.equals(allowAttachment.invoke(gunHandle, attachment));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    static void installAttachment(Object gunHandle, net.minecraft.world.item.ItemStack gun,
                                  net.minecraft.world.item.ItemStack attachment) {
        try {
            if (installAttachment != null) {
                installAttachment.invoke(gunHandle, gun, attachment);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.debug("TaCZ refused an attachment: {}", exception.toString());
        }
    }

    static ResourceLocation attachedId(Object gunHandle, net.minecraft.world.item.ItemStack gun, Object type) {
        try {
            if (getAttachmentId == null) {
                getAttachmentId = Class.forName(IGUN_CLASS)
                        .getMethod("getAttachmentId", net.minecraft.world.item.ItemStack.class,
                                Class.forName(ATTACHMENT_TYPE_CLASS));
            }
            Object value = getAttachmentId.invoke(gunHandle, gun, type);
            return value instanceof ResourceLocation id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static volatile Method getAttachmentId;

    static Object attachmentType(net.minecraft.world.item.ItemStack attachment) {
        try {
            if (attachmentTypeOf == null) {
                Class<?> type = Class.forName(IATTACHMENT_CLASS);
                Class<?> instance = Class.forName("com.tacz.guns.api.item.IAttachment");
                Method orNull = instance.getMethod("getIAttachmentOrNull", net.minecraft.world.item.ItemStack.class);
                attachmentTypeOf = instance.getMethod("getType", net.minecraft.world.item.ItemStack.class);
                handleLookup = orNull;
            }
            Object handle = handleLookup.invoke(null, attachment);
            return handle == null ? null : attachmentTypeOf.invoke(handle, attachment);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static volatile Method handleLookup;

    private static Collection<Map.Entry<?, ?>> entries(Method method) {
        if (method == null) {
            return List.of();
        }
        try {
            Object value = method.invoke(null);
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Collection<Map.Entry<?, ?>> entries = (Collection<Map.Entry<?, ?>>) (Collection<?>) map.entrySet();
                return entries;
            }
            if (value instanceof Collection<?> collection) {
                @SuppressWarnings("unchecked")
                Collection<Map.Entry<?, ?>> raw = (Collection<Map.Entry<?, ?>>) collection;
                return raw;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ReturnCapsuleMod.LOGGER.warn("TaCZ API call {} failed", method.getName(), exception);
        }
        return List.of();
    }

    static GunAccessors gunAccessors(Object index) {
        return GUN_ACCESSORS.get(index.getClass());
    }

    static GunDataAccessors gunDataAccessors(Object gunData) {
        return GUN_DATA_ACCESSORS.get(gunData.getClass());
    }

    static String readType(Object index) {
        GunAccessors accessors = gunAccessors(index);
        if (!accessors.supported()) {
            return "";
        }
        try {
            return String.valueOf(accessors.type().invoke(index)).toLowerCase(java.util.Locale.ROOT);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "";
        }
    }

    static Object readGunData(Object index) {
        GunAccessors accessors = gunAccessors(index);
        if (!accessors.supported()) {
            return null;
        }
        try {
            return accessors.gunData().invoke(index);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    static ResourceLocation readAmmoId(Object gunData) {
        GunDataAccessors accessors = gunDataAccessors(gunData);
        if (!accessors.supported()) {
            return null;
        }
        try {
            Object value = accessors.ammoId().invoke(gunData);
            return value instanceof ResourceLocation id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    static String readFireMode(Object gunData) {
        GunDataAccessors accessors = gunDataAccessors(gunData);
        if (!accessors.supported()) {
            return "SEMI";
        }
        try {
            Object modes = accessors.fireModes().invoke(gunData);
            if (modes instanceof Collection<?> collection && !collection.isEmpty()) {

                for (Object mode : collection) {
                    if ("AUTO".equalsIgnoreCase(String.valueOf(mode))) {
                        return "AUTO";
                    }
                }
                return String.valueOf(collection.iterator().next());
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {

        }
        return "SEMI";
    }

    record GunAccessors(Method type, Method gunData) {
        static final GunAccessors UNSUPPORTED = new GunAccessors(null, null);

        boolean supported() {
            return this.type != null;
        }
    }

    record GunDataAccessors(Method ammoId, Method fireModes) {
        static final GunDataAccessors UNSUPPORTED = new GunDataAccessors(null, null);

        boolean supported() {
            return this.ammoId != null;
        }
    }

    private TaczApi() {
    }
}
