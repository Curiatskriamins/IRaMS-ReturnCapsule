package com.curiats.returncapsule.compat;

import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class BossBars {

    private static final java.util.Map<Class<?>, Boolean> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private BossBars() {
    }

    public static boolean hasBar(Entity entity) {
        if (CACHE.get(entity.getClass()) == Boolean.TRUE) {
            return true;
        }
        boolean found = scan(entity);
        if (found) {
            CACHE.put(entity.getClass(), Boolean.TRUE);
        }
        return found;
    }

    private static boolean scan(Entity entity) {
        Class<?> entityClass = entity.getClass();
        if (net.minecraft.world.entity.boss.wither.WitherBoss.class.isAssignableFrom(entityClass)
                || net.minecraft.world.entity.boss.enderdragon.EnderDragon.class.isAssignableFrom(entityClass)) {
            return true;
        }
        Class<?> type = entityClass;
        while (type != null && type != Entity.class) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || !BossEvent.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    if (method.invoke(entity) instanceof BossEvent) {
                        return true;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (!BossEvent.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(entity) instanceof BossEvent) {
                        return true;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
