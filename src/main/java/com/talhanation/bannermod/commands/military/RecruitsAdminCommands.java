package com.talhanation.bannermod.commands.military;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.*;

public class RecruitsAdminCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(createRootCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRootCommand() {
        return Commands.literal("recruits")
                .requires(source -> source.hasPermission(2))
                .then(createAdminCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createAdminCommand() {
        return Commands.literal("admin")
                .then(createTeleportToOwnerCommand())
                .then(UnitsManagerAdminCommands.create())
                .then(ClaimManagerAdminCommands.create())
                .then(NobleVillagerManagerAdminCommands.create())
                .then(DebugManagerAdminCommands.create())
                .then(GovernorDebugCommands.create());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createTeleportToOwnerCommand() {
        return Commands.literal("tpRecruitsToOwner")
                .then(Commands.argument("Owner", ScoreHolderArgument.scoreHolders())
                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                        .executes(context -> RecruitOwnerTeleportHelper.teleportToOwners(
                                context.getSource().getLevel(),
                                ScoreHolderArgument.getNamesWithDefaultWildcard(context, "Owner"))));
    }
}
