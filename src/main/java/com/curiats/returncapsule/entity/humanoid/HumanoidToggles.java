package com.curiats.returncapsule.entity.humanoid;

import java.util.EnumSet;

/**
 * Runtime switches for testing: which parts of a humanoid's kit it is allowed to use.
 * <p>
 * This exists so a humanoid can be stripped of a weapon system without editing configs or rebuilding, e.g. to test
 * melee behaviour without being shot at. Toggles are global and live for the current game session only.
 */
public final class HumanoidToggles {
    public enum Slot {
        /** Both weapon slots at once: ranged and melee. Does not affect the shield. */
        ITEM("item"),
        RANGED("ranged"),
        MELEE("melee"),
        SHIELD("shield");

        private final String id;

        Slot(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static Slot byName(String name) {
            for (Slot slot : values()) {
                if (slot.id.equalsIgnoreCase(name)) {
                    return slot;
                }
            }
            return null;
        }
    }

    private static final EnumSet<Slot> DISABLED = EnumSet.noneOf(Slot.class);

    public static boolean isEnabled(Slot slot) {
        if (DISABLED.contains(slot)) {
            return false;
        }
        // "item" is the umbrella switch for the two weapon slots; it never touches the shield.
        if (slot == Slot.RANGED || slot == Slot.MELEE) {
            return !DISABLED.contains(Slot.ITEM);
        }
        return true;
    }

    public static void set(Slot slot, boolean enabled) {
        if (enabled) {
            DISABLED.remove(slot);
        } else {
            DISABLED.add(slot);
        }
    }

    public static void clear() {
        DISABLED.clear();
    }

    public static String describe() {
        StringBuilder text = new StringBuilder("ranged=").append(isEnabled(Slot.RANGED) ? 1 : 0)
                .append(" melee=").append(isEnabled(Slot.MELEE) ? 1 : 0)
                .append(" shield=").append(isEnabled(Slot.SHIELD) ? 1 : 0);
        if (DISABLED.contains(Slot.ITEM)) {
            text.append(" (item umbrella is off)");
        }
        return text.toString();
    }

    private HumanoidToggles() {
    }
}
