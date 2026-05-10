package com.talhanation.bannermod.commands.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;
import com.talhanation.bannermod.network.messages.military.MessageToClientRunVisualScenario;
import com.talhanation.bannermod.scenario.VisualScenarioIds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class VisualScenarioCommands {
    private static final double RECRUIT_SEARCH_RADIUS = 16.0D;
    private static final SimpleCommandExceptionType UNKNOWN_SCENARIO = new SimpleCommandExceptionType(
            Component.translatable("commands.bannermod.scenario.unknown"));
    private static final SimpleCommandExceptionType RECRUIT_REQUIRED = new SimpleCommandExceptionType(
            Component.translatable("commands.bannermod.scenario.recruit_required"));

    private VisualScenarioCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("scenario")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("run")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(VisualScenarioCommands::suggestScenarios)
                                .executes(context -> startScenario(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("skilltree")
                        .then(Commands.literal("player")
                                .executes(context -> startScenario(context.getSource(), VisualScenarioIds.SKILLTREE_PLAYER)))
                        .then(Commands.literal("recruit")
                                .executes(context -> startScenario(context.getSource(), VisualScenarioIds.SKILLTREE_RECRUIT))))
                .then(Commands.literal("military_command")
                        .executes(context -> startScenario(context.getSource(), VisualScenarioIds.MILITARY_COMMAND)))
                .then(Commands.literal("recruit_inventory")
                        .executes(context -> startScenario(context.getSource(), VisualScenarioIds.RECRUIT_INVENTORY)))
                .then(Commands.literal("recruit_groups")
                        .executes(context -> startScenario(context.getSource(), VisualScenarioIds.RECRUIT_GROUPS)))
                .then(Commands.literal("recruit_action_feedback")
                        .executes(context -> startScenario(context.getSource(), VisualScenarioIds.RECRUIT_ACTION_FEEDBACK)))
                .then(Commands.literal("war_room")
                        .executes(context -> startScenario(context.getSource(), VisualScenarioIds.WAR_ROOM)))
                .then(Commands.literal("political_entities")
                        .executes(context -> startScenario(context.getSource(), VisualScenarioIds.POLITICAL_ENTITIES)))
                .then(Commands.literal("war_declare")
                        .executes(context -> startScenario(context.getSource(), VisualScenarioIds.WAR_DECLARE)))
                .then(Commands.literal("world_map")
                        .executes(context -> startScenario(context.getSource(), VisualScenarioIds.WORLD_MAP)))
                .then(Commands.literal("stop")
                        .executes(context -> stop(context.getSource())));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("commands.bannermod.scenario.list", String.join(", ", VisualScenarioIds.all())), false);
        return VisualScenarioIds.all().size();
    }

    private static CompletableFuture<Suggestions> suggestScenarios(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (String id : VisualScenarioIds.all()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    }

    private static int startScenario(CommandSourceStack source, String scenario) throws CommandSyntaxException {
        if (!VisualScenarioIds.isKnown(scenario)) throw UNKNOWN_SCENARIO.create();
        ServerPlayer player = source.getPlayerOrException();
        int recruitEntityId = -1;
        if (VisualScenarioIds.usesNearestRecruit(scenario)) {
            AbstractRecruitEntity recruit = nearestRecruit(player);
            if (recruit == null && VisualScenarioIds.requiresRecruit(scenario)) throw RECRUIT_REQUIRED.create();
            if (recruit != null) recruitEntityId = recruit.getId();
        }
        send(player, scenario, recruitEntityId);
        source.sendSuccess(() -> Component.translatable("commands.bannermod.scenario.started",
                Component.translatable(VisualScenarioIds.labelKey(scenario))), false);
        return 1;
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        send(player, VisualScenarioIds.STOP, -1);
        source.sendSuccess(() -> Component.translatable("commands.bannermod.scenario.stopped"), false);
        return 1;
    }

    private static AbstractRecruitEntity nearestRecruit(ServerPlayer player) {
        List<AbstractRecruitEntity> recruits = RecruitIndex.instance().allInRange(player.serverLevel(), player.position(), RECRUIT_SEARCH_RADIUS, true);
        if (recruits == null || recruits.isEmpty()) return null;
        return recruits.stream()
                .min(Comparator.comparingDouble(recruit -> recruit.distanceToSqr(player)))
                .orElse(null);
    }

    private static void send(ServerPlayer player, String scenario, int recruitEntityId) {
        BannerModMain.SIMPLE_CHANNEL.send(BannerModPacketDistributor.PLAYER.with(() -> player),
                new MessageToClientRunVisualScenario(scenario, recruitEntityId));
    }
}
