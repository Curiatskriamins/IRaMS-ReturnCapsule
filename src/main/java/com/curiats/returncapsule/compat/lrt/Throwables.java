package com.curiats.returncapsule.compat.lrt;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Throwables {

    public record ThrowableInfo(ResourceLocation id, double initialSpeed, int lifeTime, float gravity,
                                boolean contact, double damage, float radius, boolean explosive,
                                boolean cloud, boolean destroysBlocks, boolean remote) {
    }

    private static boolean resolved;
    private static boolean broken;
    private static Method getThrowableIndex;
    private static Method getThrowableIndexes;
    private static Method getData;
    private static Method getEntityDataAccessor;

    private static Method onThrow;
    private static Method dataInitialSpeed;
    private static Method dataCookable;
    private static Method entityLifeTime;
    private static Method entityGravity;
    private static Method entityBrokeOnGround;
    private static Method getExplode;
    private static Method explodeDamage;
    private static Method explodeRadius;
    private static Method explodeDestroy;
    private static Method explodeRemote;

    private Throwables() {
    }

    public static boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().equals("ThrowableItem")) {
                return true;
            }
        }
        return false;
    }

    public static ThrowableInfo describe(ItemStack stack) {
        if (!resolve() || !isThrowable(stack)) {
            return null;
        }
        try {
            Object index = getThrowableIndex.invoke(stack.getItem(), stack);
            if (index instanceof java.util.Optional<?> optional && optional.isEmpty()) {
                return null;
            }
            Object resolvedIndex = index instanceof java.util.Optional<?> optional ? optional.get() : index;
            Object data = getData.invoke(resolvedIndex);
            Object entityData = getEntityDataAccessor.invoke(data);
            ResourceLocation id = (ResourceLocation) resolvedIndex.getClass().getMethod("getId").invoke(resolvedIndex);
            boolean cloud = data.getClass().getSimpleName().contains("Cloud");
            boolean explosive = false;
            boolean destroysBlocks = false;
            boolean remote = false;
            double damage = 0.0D;
            float radius = 0.0F;
            if (getExplode != null) {
                Object explode = getExplode.invoke(data);
                if (explode != null) {
                    explosive = true;
                    damage = ((Number) explodeDamage.invoke(explode)).doubleValue();
                    radius = ((Number) explodeRadius.invoke(explode)).floatValue();
                    if (explodeDestroy != null) {
                        destroysBlocks = (Boolean) explodeDestroy.invoke(explode);
                    }
                    if (explodeRemote != null) {
                        remote = (Boolean) explodeRemote.invoke(explode);
                    }
                }
            }
            double speed = ((Number) dataInitialSpeed.invoke(data)).doubleValue();
            int life = ((Number) entityLifeTime.invoke(entityData)).intValue();
            float gravity = ((Number) entityGravity.invoke(entityData)).floatValue();
            boolean contact = (Boolean) entityBrokeOnGround.invoke(entityData);
            return new ThrowableInfo(id, speed, life, gravity, contact, damage, radius, explosive, cloud,
                    destroysBlocks, remote);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    public static boolean throwAt(Level level, LivingEntity thrower, ItemStack stack, float pitch, float yaw) {
        if (!resolve()) {
            return false;
        }
        try {
            thrower.setXRot(pitch);
            thrower.setYRot(yaw);
            thrower.yBodyRot = yaw;
            thrower.yHeadRot = yaw;
            Object index = getThrowableIndex.invoke(stack.getItem(), stack);
            if (!(index instanceof java.util.Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            onThrow.invoke(stack.getItem(), level, thrower, stack, optional.get());
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    public static java.util.List<ItemStack> available() {
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        if (!resolve() || getThrowableIndexes == null) {
            return stacks;
        }
        try {
            Object collection = getThrowableIndexes.invoke(null);
            if (!(collection instanceof java.util.Collection<?> values)) {
                return stacks;
            }
            for (Object index : values) {
                Object stack = index.getClass().getMethod("createItemStack").invoke(index);
                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    stacks.add(itemStack);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return stacks;
        }
        return stacks;
    }

    private static boolean resolve() {
        if (resolved) {
            return !broken;
        }
        resolved = true;
        try {
            Class<?> throwableInterface = Class.forName("me.xjqsh.lrtactical.api.item.IThrowable");
            getThrowableIndex = throwableInterface.getMethod("getThrowableIndex", ItemStack.class);
            Class<?> apiClass = Class.forName("me.xjqsh.lrtactical.api.LrTacticalAPI");
            getThrowableIndexes = apiClass.getMethod("getThrowableIndexes");
            Class<?> indexClass = Class.forName("me.xjqsh.lrtactical.item.index.ThrowableIndex");
            getData = indexClass.getMethod("getData");
            Class<?> dataClass = Class.forName("me.xjqsh.lrtactical.item.throwable.ThrowableData");
            getEntityDataAccessor = dataClass.getMethod("getEntityData");
            dataInitialSpeed = dataClass.getMethod("getInitialSpeed");
            Class<?> entityDataClass = Class.forName("me.xjqsh.lrtactical.item.throwable.EntityData");
            entityLifeTime = entityDataClass.getMethod("getLifeTime");
            entityGravity = entityDataClass.getMethod("getGravity");
            entityBrokeOnGround = entityDataClass.getMethod("isBrokeOnGround");
            Class<?> itemClass = Class.forName("me.xjqsh.lrtactical.item.ThrowableItem");
            onThrow = itemClass.getMethod("onThrow", Level.class, LivingEntity.class, ItemStack.class, indexClass);
            try {
                Class<?> explodeClass = Class.forName("me.xjqsh.lrtactical.item.throwable.explode.ExplodeThrowableData");
                getExplode = explodeClass.getMethod("getExplode");
                Class<?> explodeData = Class.forName(
                        "me.xjqsh.lrtactical.item.throwable.explode.ExplodeThrowableData$ExplodeData");
                explodeDamage = explodeData.getMethod("getDamage");
                explodeRadius = explodeData.getMethod("getRadius");
                explodeDestroy = explodeData.getMethod("isDestroyBlocks");
                try {
                    explodeRemote = explodeData.getMethod("isRemoteDetonation");
                } catch (NoSuchMethodException ignored) {
                    explodeRemote = null;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                getExplode = null;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            broken = true;
            return false;
        }
    }
}
