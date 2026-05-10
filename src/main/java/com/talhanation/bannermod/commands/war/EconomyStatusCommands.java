package com.talhanation.bannermod.commands.war;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.settlement.SettlementOrchestrator;
import com.talhanation.bannermod.settlement.economy.FortUpgradeService;
import com.talhanation.bannermod.settlement.economy.NpcDemandContractService;
import com.talhanation.bannermod.settlement.economy.StrategicResourceAccountingManager;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.runtime.TreatyDefaultFact;
import com.talhanation.bannermod.war.runtime.TreatyRuntime;
import com.talhanation.bannermod.war.runtime.TributeTreatyRecord;
import com.talhanation.bannermod.war.runtime.VassalRelationshipRecord;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

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
                                .executes(EconomyStatusCommands::contracts)
                                .then(Commands.literal("complete")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("contractUuid", StringArgumentType.word())
                                                .executes(EconomyStatusCommands::completeContract)))))
                .then(Commands.literal("upgradeFort")
                        .then(Commands.argument("claimUuid", StringArgumentType.word())
                                .executes(EconomyStatusCommands::upgradeFort)))
                .then(Commands.literal("treaties")
                        .executes(EconomyStatusCommands::treaties));
    }

    private static int contracts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = overworldServerLevel(context.getSource());
        UUID claimUuid = parseUuid(context, "claimUuid");
        if (claimUuid == null) {
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

    private static int upgradeFort(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (level.dimension() != Level.OVERWORLD) {
            context.getSource().sendFailure(Component.translatable("chat.bannermod.fort.upgrade.denied.overworld_only"));
            return 0;
        }
        UUID claimUuid = parseUuid(context, "claimUuid");
        if (claimUuid == null) {
            return 0;
        }
        RecruitsClaimManager claimManager = ClaimEvents.claimManager();
        if (claimManager == null) {
            context.getSource().sendFailure(Component.translatable("chat.bannermod.fort.upgrade.denied.missing_claim"));
            return 0;
        }
        RecruitsClaim claim = claimManager.getAllClaims().stream()
                .filter(candidate -> candidate.getUUID().equals(claimUuid))
                .findFirst()
                .orElse(null);
        PoliticalEntityRecord politicalOwner = claim != null && claim.getOwnerPoliticalEntityId() != null
                ? WarRuntimeContext.registry(level).byId(claim.getOwnerPoliticalEntityId()).orElse(null)
                : null;
        BannerModTreasuryManager treasuryManager = BannerModTreasuryManager.get(level);
        StrategicResourceAccountingManager accountingManager = StrategicResourceAccountingManager.get(level);
        FortUpgradeService.UpgradeResult result = FortUpgradeService.planUpgrade(
                treasuryManager,
                accountingManager,
                claim,
                player.getUUID(),
                player.isCreative() && player.hasPermissions(2),
                politicalOwner
        );
        if (!result.upgraded()) {
            if (result.missingBucket() != null) {
                context.getSource().sendFailure(Component.translatable(
                        result.messageKey(),
                        result.missingAmount(),
                        Component.translatable("resource.bannermod.strategic." + result.missingBucket().id())
                ));
            } else {
                context.getSource().sendFailure(Component.translatable(result.messageKey()));
            }
            return 0;
        }

        RecruitsClaim upgradedClaim = RecruitsClaim.fromNBT(claim.toNBT());
        int previousFortLevel = upgradedClaim.getFortLevel();
        boolean persisted = claimManager.addOrUpdateClaimIf(level, upgradedClaim, () -> FortUpgradeService.applyUpgrade(
                treasuryManager,
                accountingManager,
                upgradedClaim,
                result,
                level.getGameTime()
        ), () -> FortUpgradeService.rollbackUpgrade(
                treasuryManager,
                accountingManager,
                upgradedClaim,
                previousFortLevel,
                result,
                level.getGameTime()
        ));
        if (!persisted) {
            context.getSource().sendFailure(Component.translatable("chat.bannermod.fort.upgrade.denied.persistence_veto"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                result.messageKey(),
                upgradedClaim.getName(),
                result.newLevel(),
                Component.translatable("resource.bannermod.fort_level." + result.newLevel())
        ), true);
        return result.newLevel();
    }

    private static int completeContract(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = overworldServerLevel(context.getSource());
        UUID claimUuid = parseUuid(context, "claimUuid");
        UUID contractUuid = parseUuid(context, "contractUuid");
        if (claimUuid == null || contractUuid == null) {
            return 0;
        }
        NpcDemandContractService service = SettlementOrchestrator.npcDemandContractService(level);
        if (service == null) {
            context.getSource().sendFailure(Component.literal("NPC demand contract service unavailable"));
            return 0;
        }
        NpcDemandContractService.CompletionResult result = service.completeContract(level, claimUuid, contractUuid, level.getGameTime());
        if (!result.success()) {
            context.getSource().sendFailure(Component.literal("Contract completion failed: " + result.reason()));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.literal("Completed contract " + contractUuid
                        + ": delivered " + result.deliveredAmount() + " " + result.deliveredBucket().id()
                        + ", reward " + result.rewardCoins() + " coins"),
                true
        );
        return 1;
    }

    private static UUID parseUuid(CommandContext<CommandSourceStack> context, String argumentName) {
        try {
            return UUID.fromString(StringArgumentType.getString(context, argumentName));
        } catch (IllegalArgumentException exception) {
            if ("claimUuid".equals(argumentName)) {
                context.getSource().sendFailure(Component.translatable("chat.bannermod.economy.denied.invalid_claim_uuid"));
            } else {
                context.getSource().sendFailure(Component.literal(argumentName + " must be a UUID"));
            }
            return null;
        }
    }

    private static ServerLevel serverLevel(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getLevel().isClientSide()) {
            throw SERVER_ONLY.create();
        }
        return source.getLevel();
    }

    private static int treaties(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = overworldServerLevel(source);
        TreatyRuntime runtime = WarRuntimeContext.treaties(level);
        int lines = 0;
        source.sendSuccess(() -> Component.literal("Tribute treaties:"), false);
        for (TributeTreatyRecord treaty : runtime.tributeTreaties()) {
            lines++;
            source.sendSuccess(() -> Component.literal(
                    "- " + treaty.id()
                            + " payer=" + treaty.payerEntityId()
                            + " receiver=" + treaty.receiverEntityId()
                            + " resource=" + treaty.resourceBucket().id()
                            + " amount=" + treaty.amount()
                            + " intervalTicks=" + treaty.intervalTicks()
                            + " sourceClaim=" + treaty.sourceClaimUuid()
                            + " missed=" + treaty.missedPayments()
                            + " defaulted=" + treaty.defaultedAmount()
                            + " active=" + treaty.active()), false);
        }
        source.sendSuccess(() -> Component.literal("Vassal relationships:"), false);
        for (VassalRelationshipRecord relationship : runtime.vassalRelationships()) {
            lines++;
            source.sendSuccess(() -> Component.literal(
                    "- " + relationship.id()
                            + " overlord=" + relationship.overlordEntityId()
                            + " vassal=" + relationship.vassalEntityId()
                            + " obligations=" + relationship.obligations()
                            + " tributeAmount=" + relationship.tributeAmount()
                            + " active=" + relationship.active()), false);
        }
        source.sendSuccess(() -> Component.literal("Treaty-default facts:"), false);
        for (TreatyDefaultFact fact : runtime.defaultFacts()) {
            lines++;
            source.sendSuccess(() -> Component.literal(
                    "- " + fact.id()
                            + " treaty=" + fact.treatyId()
                            + " payer=" + fact.payerEntityId()
                            + " receiver=" + fact.receiverEntityId()
                            + " type=" + fact.defaultType()
                            + " requested=" + fact.requestedAmount()
                            + " paid=" + fact.paidAmount()
                            + " defaulted=" + fact.defaultedAmount()
                            + " tick=" + fact.gameTime()), false);
        }
        if (lines == 0) {
            source.sendSuccess(() -> Component.literal("No active treaty records or defaults."), false);
        }
        return lines;
    }

    private static ServerLevel overworldServerLevel(CommandSourceStack source) throws CommandSyntaxException {
        serverLevel(source);
        return source.getServer().overworld();
    }
}
