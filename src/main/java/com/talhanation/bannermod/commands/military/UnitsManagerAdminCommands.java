package com.talhanation.bannermod.commands.military;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class UnitsManagerAdminCommands {
    private UnitsManagerAdminCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("unitsManager")
                .then(Commands.literal("getUnitsCount")
                        .then(Commands.argument("Player", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .executes(context -> {
                                    ServerPlayer player = getPlayer(context);
                                    if (player == null) {
                                        return 0;
                                    }

                                    int unitCount = getUnitsCount(player);
                                    context.getSource().sendSuccess(() ->
                                            Component.literal(player.getName().getString() + " has " + unitCount + " from max. " + RecruitsServerConfig.MaxRecruitsForPlayer.get()), false);
                                    return 1;
                                })))
                .then(Commands.literal("setUnitsCount")
                        .then(Commands.argument("Player", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .then(Commands.argument("Amount", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            ServerPlayer player = getPlayer(context);
                                            if (player == null) {
                                                return 0;
                                            }

                                            int amount = IntegerArgumentType.getInteger(context, "Amount");
                                            return setUnitsCount(context, player, amount);
                                        }))));
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String playerName = ScoreHolderArgument.getName(context, "Player").getScoreboardName();
        ServerPlayer player = context.getSource().getLevel().getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("No Player found!").withStyle(ChatFormatting.RED));
        }
        return player;
    }

    private static int getUnitsCount(ServerPlayer player) {
        if (RecruitEvents.playerUnitManager() != null) {
            return RecruitEvents.playerUnitManager().getRecruitCount(player.getUUID());
        }

        return 0;
    }

    private static int setUnitsCount(CommandContext<CommandSourceStack> context, ServerPlayer player, int amount) {
        if (RecruitEvents.playerUnitManager() != null) {
            RecruitEvents.playerUnitManager().setRecruitCount(player, amount);
            RecruitEvents.playerUnitManager().save(context.getSource().getLevel());
            context.getSource().sendSuccess(() ->
                    Component.literal("The recruits count of " + player.getName().getString() + " has been set to " + amount + "."), false);
            return 1;
        }
        return 0;
    }
}
