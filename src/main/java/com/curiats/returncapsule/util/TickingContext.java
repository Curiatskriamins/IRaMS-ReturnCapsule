package com.curiats.returncapsule.util;

import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TickingContext {

    private static final ThreadLocal<Deque<Entity>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private TickingContext() {
    }

    public static Entity current() {
        Deque<Entity> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public static void enter(Entity entity) {
        Deque<Entity> stack = STACK.get();
        if (stack.size() > 32) {
            stack.clear();
        }
        stack.push(entity);
    }

    public static void exit() {
        Deque<Entity> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }
}
