package com.talhanation.bannermod.commands.war;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.talhanation.bannermod.commands.society.BannerModSocietyCommands;
import com.talhanation.bannermod.commands.admin.AdminDebugCommands;
import com.talhanation.bannermod.commands.admin.AdminRecoveryCommands;
import com.talhanation.bannermod.commands.admin.VisualScenarioCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class BannerModWarCommands {
    private BannerModWarCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("bannermod")
                .then(BannerModSocietyCommands.build())
                .then(AdminDebugCommands.debug())
                .then(AdminRecoveryCommands.settlement())
                .then(AdminRecoveryCommands.treasury())
                .then(AdminRecoveryCommands.claim())
                .then(AdminRecoveryCommands.worker())
                .then(EconomyStatusCommands.build())
                .then(VisualScenarioCommands.build())
                .then(PoliticalRegistryCommands.build())
                .then(WarDeclarationCommands.build()
                        .then(SiegeStandardCommands.build())
                        .then(WarAllyCommands.build()));
    }
}
