package com.curiats.returncapsule.compat;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Windowed trace of the whole humanoid damage pipeline, one report per minute.
 *
 * <p>{@code NeutralHumanoid.hurt} is a stack of independent filters - friendly fire, shield, low damage immunity, the
 * four tick gate, the flat reduction, the per hit cap, last stand, and finally vanilla armour inside
 * {@code super.hurt}. When a humanoid still melts, the only way to tell which of them actually fired (and how much
 * each one saved) is to count them, and who hit it with what.</p>
 */
public final class HumanoidDamageTrace {

    private static final long WINDOW_TICKS = 1200L;

    private static final int MAX_SOURCES = 64;

    private static final int REPORTED_SOURCES = 8;

    private static long windowStart = Long.MIN_VALUE;

    private static int conversions;
    private static int conversionRefused;
    private static float conversionTotal;
    private static float conversionLargest;

    private static int rejectedFriendly;
    private static int rejectedLow;
    private static int rejectedGate;
    private static int shieldBlocks;
    private static int cappedHits;
    private static int budgetCappedHits;
    private static int budgetBlockedHits;
    private static int lastStands;
    private static int accepted;

    private static float incomingTotal;
    private static float afterReductionTotal;
    private static float reductionSaved;
    private static float armorSaved;
    private static float healthLostTotal;
    private static float largestIncoming;
    private static int armourSeen;
    private static float toughnessSeen;

    private static final Map<String, Source> SOURCES = new HashMap<>();

    public static String keyFor(DamageSource source) {
        String type = source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElseGet(source::getMsgId);
        Entity attacker = source.getEntity();
        if (attacker == null) {
            return type + " <- environment";
        }
        net.minecraft.resources.ResourceLocation entityId =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(attacker.getType());
        return type + " <- " + entityId + "(" + attacker.getName().getString() + ")";
    }

    public static void flushIfDue(long gameTime) {
        if (windowStart == Long.MIN_VALUE) {
            windowStart = gameTime;
            return;
        }
        if (gameTime - windowStart < WINDOW_TICKS) {
            return;
        }
        if (conversions + rejectedFriendly + rejectedLow + rejectedGate + shieldBlocks + accepted > 0) {
            ReturnCapsuleMod.LOGGER.info(
                    "[humanoid] damage trace ({}t): setHealthWrites={} convRefused={} friendly={} low={} gate={} shield={} cap={} budgetHit={} budgetBlock={} lastStand={} accepted={}",
                    gameTime - windowStart, conversions, conversionRefused, rejectedFriendly, rejectedLow,
                    rejectedGate, shieldBlocks, cappedHits, budgetCappedHits, budgetBlockedHits, lastStands,
                    accepted);
            ReturnCapsuleMod.LOGGER.info(
                    "[humanoid] damage totals: incoming={} afterReduction={} reductionSaved={} armor={} toughness={} armorSaved={} healthLost={} largestIn={} largestConverted={}",
                    Math.round(incomingTotal), Math.round(afterReductionTotal), Math.round(reductionSaved),
                    armourSeen, Math.round(toughnessSeen), Math.round(armorSaved), Math.round(healthLostTotal),
                    Math.round(largestIncoming), Math.round(conversionLargest));
            reportSources();
        }
        reset(gameTime);
    }

    private static void reportSources() {
        List<Map.Entry<String, Source>> entries = new ArrayList<>(SOURCES.entrySet());
        entries.sort((left, right) -> Float.compare(right.getValue().incoming, left.getValue().incoming));
        int limit = Math.min(REPORTED_SOURCES, entries.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Source> entry = entries.get(i);
            Source source = entry.getValue();
            ReturnCapsuleMod.LOGGER.info(
                    "[humanoid] source {}: hits={} rejected={} incoming={} largest={} lost={}",
                    entry.getKey(), source.hits, source.rejected, Math.round(source.incoming),
                    Math.round(source.largest), Math.round(source.lost));
        }
    }

    private static void reset(long gameTime) {
        windowStart = gameTime;
        conversions = 0;
        conversionRefused = 0;
        conversionTotal = 0.0F;
        conversionLargest = 0.0F;
        rejectedFriendly = 0;
        rejectedLow = 0;
        rejectedGate = 0;
        shieldBlocks = 0;
        cappedHits = 0;
        budgetCappedHits = 0;
        budgetBlockedHits = 0;
        lastStands = 0;
        accepted = 0;
        incomingTotal = 0.0F;
        afterReductionTotal = 0.0F;
        reductionSaved = 0.0F;
        armorSaved = 0.0F;
        healthLostTotal = 0.0F;
        largestIncoming = 0.0F;
        SOURCES.clear();
    }

    private static Source source(String key) {
        Source known = SOURCES.get(key);
        if (known != null) {
            return known;
        }
        if (SOURCES.size() >= MAX_SOURCES) {
            return SOURCES.computeIfAbsent("other", ignored -> new Source());
        }
        Source created = new Source();
        SOURCES.put(key, created);
        return created;
    }

    public static void converted(long gameTime, float loss, boolean applied) {
        flushIfDue(gameTime);
        conversions++;
        if (!applied) {
            conversionRefused++;
        }
        conversionTotal += loss;
        conversionLargest = Math.max(conversionLargest, loss);
        Source source = source("direct setHealth");
        source.hits++;
        source.incoming += loss;
        source.largest = Math.max(source.largest, loss);
        if (!applied) {
            source.rejected++;
        }
    }

    public static void rejectedFriendly(String key) {
        rejectedFriendly++;
        source(key).rejected++;
    }

    public static void rejectedLow(String key, float amount) {
        rejectedLow++;
        Source source = source(key);
        source.hits++;
        source.rejected++;
        source.incoming += amount;
        source.largest = Math.max(source.largest, amount);
        largestIncoming = Math.max(largestIncoming, amount);
    }

    public static void rejectedGate(String key, float amount) {
        rejectedGate++;
        Source source = source(key);
        source.hits++;
        source.rejected++;
        source.incoming += amount;
        source.largest = Math.max(source.largest, amount);
    }

    public static void shieldBlocked(String key) {
        shieldBlocks++;
        source(key).rejected++;
    }

    public static void observedArmour(int armour, float toughness) {
        armourSeen = armour;
        toughnessSeen = toughness;
    }

    public static void reductionApplied(float amount, float afterReduction) {
        reductionSaved += Math.max(0.0F, amount - afterReduction);
    }

    public static void capped() {
        cappedHits++;
    }

    public static void budgetCapped() {
        budgetCappedHits++;
    }

    public static void budgetBlocked() {
        budgetBlockedHits++;
    }

    public static void lastStand() {
        lastStands++;
    }

    public static void accepted(String key, long gameTime, float incoming, float afterReduction,
                                float healthBefore, float healthAfter) {
        flushIfDue(gameTime);
        accepted++;
        incomingTotal += incoming;
        afterReductionTotal += afterReduction;
        largestIncoming = Math.max(largestIncoming, incoming);
        float lost = Math.max(0.0F, healthBefore - healthAfter);
        healthLostTotal += lost;
        // Anything the reduction did not remove but the hull did not lose is vanilla armour / effects / absorption.
        armorSaved += Math.max(0.0F, afterReduction - lost);
        Source source = source(key);
        source.hits++;
        source.incoming += incoming;
        source.largest = Math.max(source.largest, incoming);
        source.lost += lost;
    }

    private static final class Source {
        private int hits;
        private int rejected;
        private float incoming;
        private float largest;
        private float lost;
    }

    private HumanoidDamageTrace() {
    }
}
