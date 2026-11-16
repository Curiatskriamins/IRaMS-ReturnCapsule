package com.curiats.returncapsule.command;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.humanoid.HumanoidToggles;
import com.curiats.returncapsule.entity.humanoid.NeutralHumanoid;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = ReturnCapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanoidCommand {
    private static final List<String> TARGETS = List.of("all", "nearby");
    private static final List<String> SLOTS = List.of("item", "ranged", "melee", "shield", "fists");

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
                .then(Commands.literal("target")
                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(4.0D, 512.0D))
                                .executes(context -> spawnTarget(context, "minecraft:zombie"))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(TARGET_TYPE_SUGGESTIONS)
                                        .executes(context -> spawnTarget(context,
                                                StringArgumentType.getString(context, "type"))))))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(TARGET_SUGGESTIONS)
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests(SLOT_SUGGESTIONS)
                                .then(Commands.argument("enabled", IntegerArgumentType.integer(0, 1))
                                        .executes(HumanoidCommand::apply)))));
    }

    private static final SuggestionProvider<CommandSourceStack> TARGET_TYPE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:warden"), builder);

    private static int spawnTarget(CommandContext<CommandSourceStack> context, String type) {
        CommandSourceStack source = context.getSource();
        double distance = DoubleArgumentType.getDouble(context, "distance");
        ServerLevel level = source.getLevel();
        net.minecraft.world.phys.Vec3 eye = source.getPosition();
        net.minecraft.world.phys.Vec2 rotation = source.getRotation();
        double yaw = Math.toRadians(rotation.y);
        double pitch = Math.toRadians(rotation.x);
        double x = eye.x - Math.sin(yaw) * Math.cos(pitch) * distance;
        double y = eye.y - Math.sin(pitch) * distance;
        double z = eye.z + Math.cos(yaw) * Math.cos(pitch) * distance;

        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(type);
        net.minecraft.world.entity.EntityType<?> entityType =
                id == null ? null : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (entityType == null) {
            source.sendFailure(Component.literal("[returncapsule] unknown entity type '" + type + "'"));
            return 0;
        }
        net.minecraft.world.entity.Entity spawned = entityType.create(level);
        if (spawned == null) {
            source.sendFailure(Component.literal("[returncapsule] could not create '" + type + "'"));
            return 0;
        }
        spawned.moveTo(x, y, z, rotation.y, 0.0F);
        level.addFreshEntity(spawned);
        float actual = (float) Math.sqrt(spawned.distanceToSqr(eye.x, eye.y, eye.z));
        source.sendSuccess(() -> Component.literal(
                "[returncapsule] spawned " + type + " at " + Math.round(actual) + " blocks"), true);
        return 1;
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

        if ("mobends".equalsIgnoreCase(slotName)) {

            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
                com.curiats.returncapsule.compat.mobends.MobendsAddon.setAnimationEnabled(enabled == 1);
                source.sendSuccess(() -> Component.literal("[returncapsule] mobends animation=" + enabled), true);
                return 1;
            }
            source.sendFailure(Component.literal("[returncapsule] mobends toggle is client only"));
            return 0;
        }
        if ("fists".equalsIgnoreCase(slotName)) {

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
