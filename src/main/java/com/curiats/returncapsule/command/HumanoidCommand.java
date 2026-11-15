package com.curiats.returncapsule.command;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.HumanoidToggles;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /returncapsule <target> <slot> <0|1>} — turn parts of a humanoid's kit on or off for testing, plus an
 * {@code info} branch showing the current state.
 * <p>
 * Weapons can be disabled without touching configs, which is what makes it practical to test one behaviour at a time:
 * {@code /returncapsule all item 0} leaves the humanoid with fists only.
 */
@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanoidCommand {
    private static final List<String> TARGETS = List.of("all", "nearby");
    private static final List<String> SLOTS = List.of("item", "ranged", "melee", "shield", "fists");
    /** Radius used by the {@code nearby} target, in blocks. */
    private static final double NEARBY_RADIUS = 64.0D;

    private static final SuggestionProvider<CommandSourceStack> TARGET_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(TARGETS, builder);
    private static final SuggestionProvider<CommandSourceStack> SLOT_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(SLOTS, builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("returncapsule")
                .then(Commands.literal("info").executes(HumanoidCommand::info))
                .then(Commands.literal("reset").executes(HumanoidCommand::reset))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(TARGET_SUGGESTIONS)
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests(SLOT_SUGGESTIONS)
                                .then(Commands.argument("enabled", IntegerArgumentType.integer(0, 1))
                                        .executes(HumanoidCommand::apply)))));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = collect(source, "all").size();
        source.sendSuccess(() -> Component.literal(
                "[returncapsule] " + HumanoidToggles.describe() + " | humanoids loaded: " + count), false);
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        HumanoidToggles.clear();
        int count = collect(context.getSource(), "all").size();
        for (NeutralHumanoid humanoid : collect(context.getSource(), "all")) {
            humanoid.refreshLoadout();
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "[returncapsule] all toggles reset; " + count + " humanoid(s) refreshed"), true);
        return count;
    }

    private static int apply(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String target = StringArgumentType.getString(context, "target");
        String slotName = StringArgumentType.getString(context, "slot");
        int enabled = IntegerArgumentType.getInteger(context, "enabled");

        if ("fists".equalsIgnoreCase(slotName)) {
            // Convenience: strip both weapon slots in one go.
            HumanoidToggles.set(HumanoidToggles.Slot.ITEM, false);
            List<NeutralHumanoid> affected = collect(source, target);
            affected.forEach(NeutralHumanoid::refreshLoadout);
            source.sendSuccess(() -> Component.literal(
                    "[returncapsule] weapons disabled for " + affected.size() + " humanoid(s); "
                            + HumanoidToggles.describe()), true);
            return affected.size();
        }

        HumanoidToggles.Slot slot = HumanoidToggles.Slot.byName(slotName);
        if (slot == null) {
            source.sendFailure(Component.literal("unknown slot '" + slotName + "', expected one of " + SLOTS));
            return 0;
        }
        HumanoidToggles.set(slot, enabled == 1);
        List<NeutralHumanoid> affected = collect(source, target);
        affected.forEach(NeutralHumanoid::refreshLoadout);
        source.sendSuccess(() -> Component.literal(
                "[returncapsule] " + slot.id() + "=" + enabled + " for " + affected.size()
                        + " humanoid(s); " + HumanoidToggles.describe()), true);
        return affected.size();
    }

    private static List<NeutralHumanoid> collect(CommandSourceStack source, String target) {
        List<NeutralHumanoid> found = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof NeutralHumanoid humanoid)) {
                    continue;
                }
                if ("nearby".equalsIgnoreCase(target) && source.getEntity() != null
                        && humanoid.distanceToSqr(source.getPosition()) > NEARBY_RADIUS * NEARBY_RADIUS) {
                    continue;
                }
                found.add(humanoid);
            }
        }
        return found;
    }

    private HumanoidCommand() {
    }
}
