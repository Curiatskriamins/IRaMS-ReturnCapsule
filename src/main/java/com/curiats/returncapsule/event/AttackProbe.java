package com.curiats.returncapsule.event;

import com.curiats.returncapsule.ReturnCapsuleMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Temporary combat probe.
 *
 * <p>Logs every attack a player makes together with the weapon that made it and what the target actually lost, then
 * reports how long a target survived once it dies. Deliberately loose: no item filter, no damage threshold, so no hit
 * can be missed while the mimic blade's numbers are being measured. Remove once the balance pass is done.</p>
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AttackProbe {

    private record Engagement(long firstTick, float startHealth, float maxHealth, float totalDamage, int hits) {
    }

    private static final Map<UUID, Engagement> ENGAGEMENTS = new ConcurrentHashMap<>();

    /** Last tick a player hit each target; every later damage instance on it is logged too. */
    private static final Map<UUID, Long> LAST_TOUCH = new ConcurrentHashMap<>();

    private static final AtomicLong LINES = new AtomicLong();

    /** Safety valve only; far above what a short test needs, but stops a runaway log from filling the disk. */
    private static final long MAX_LINES = 5000L;

    private static boolean allowed() {
        return LINES.get() < MAX_LINES;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!allowed()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player)) {
            return;
        }
        LivingEntity target = event.getEntity();
        ItemStack held = player.getMainHandItem();
        LAST_TOUCH.put(target.getUUID(), target.level().getGameTime());
        LINES.incrementAndGet();
        ReturnCapsuleMod.LOGGER.info("[probe] {} ({}) hits {} with {} x{} ench={} src={} | hurt={} targetHp={}/{}",
                player.getName().getString(),
                String.format("%.1f", player.getAttributeValue(Attributes.ATTACK_DAMAGE)),
                target.getName().getString(),
                held.isEmpty() ? "empty" : ForgeRegistries.ITEMS.getKey(held.getItem()),
                held.getCount(),
                held.isEmpty() ? "-" : EnchantmentHelper.getEnchantments(held),
                event.getSource().getMsgId(),
                event.getAmount(),
                target.getHealth(),
                target.getMaxHealth());
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!allowed()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        LivingEntity target = event.getEntity();
        Long lastTouch = LAST_TOUCH.get(target.getUUID());
        boolean recent = lastTouch != null && target.level().getGameTime() - lastTouch < 200L;
        if (!(source instanceof Player) && !recent) {
            // Loose on purpose: the follow up damage of a blade is often applied by an effect, which carries no player
            // entity at all. Anything landing on a target the player just hit is logged too.
            return;
        }
        float before = target.getHealth() + event.getAmount();
        long now = target.level().getGameTime();
        Engagement previous = ENGAGEMENTS.get(target.getUUID());
        Engagement updated = previous == null
                ? new Engagement(now, before, target.getMaxHealth(), event.getAmount(), 1)
                : new Engagement(previous.firstTick(), previous.startHealth(), previous.maxHealth(),
                        previous.totalDamage() + event.getAmount(), previous.hits() + 1);
        ENGAGEMENTS.put(target.getUUID(), updated);
        LINES.incrementAndGet();
        ReturnCapsuleMod.LOGGER.info("[probe] landed={} on {} src={} indirect={} | hp={}/{} | hits={} total={}",
                event.getAmount(), target.getName().getString(), event.getSource().getMsgId(),
                event.getSource().getDirectEntity() != null && event.getSource().getDirectEntity() != event.getSource().getEntity(),
                target.getHealth(), target.getMaxHealth(), updated.hits(), updated.totalDamage());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        Engagement engagement = ENGAGEMENTS.remove(target.getUUID());
        if (engagement == null) {
            return;
        }
        long elapsed = target.level().getGameTime() - engagement.firstTick();
        ReturnCapsuleMod.LOGGER.info("[probe] {} died: hits={} totalDamage={} ticks={}s startHp={}/{} killer={}",
                target.getName().getString(), engagement.hits(), engagement.totalDamage(),
                String.format("%.2f", elapsed / 20.0D), engagement.startHealth(), engagement.maxHealth(),
                event.getSource().getMsgId());
    }

    private AttackProbe() {
    }
}
