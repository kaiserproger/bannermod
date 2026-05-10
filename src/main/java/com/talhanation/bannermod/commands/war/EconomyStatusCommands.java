package com.talhanation.bannermod.commands.war;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.talhanation.bannermod.settlement.SettlementOrchestrator;
import com.talhanation.bannermod.settlement.economy.NpcDemandContractService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

public final class EconomyStatusCommands {
    private static final SimpleCommandExceptionType SERVER_ONLY = new SimpleCommandExceptionType(
            Component.literal("This command can only run on a server")
    );

    private EconomyStatusCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("economy")
                .then(Commands.literal("contracts")
                        .then(Commands.argument("claimUuid", StringArgumentType.word())
                                .executes(EconomyStatusCommands::contracts)));
    }

    private static int contracts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        UUID claimUuid;
        try {
            claimUuid = UUID.fromString(StringArgumentType.getString(context, "claimUuid"));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("claimUuid must be a UUID"));
            return 0;
        }
        NpcDemandContractService service = SettlementOrchestrator.npcDemandContractService(level);
        if (service == null) {
            context.getSource().sendFailure(Component.literal("NPC demand contract service unavailable"));
            return 0;
        }
        List<String> lines = service.statusLines(claimUuid);
        for (String line : lines) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return Math.max(0, lines.size() - 1);
    }

    private static ServerLevel serverLevel(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getLevel().isClientSide()) {
            throw SERVER_ONLY.create();
        }
        return source.getLevel();
    }
}
