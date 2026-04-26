package com.talhanation.bannermod.commands.war;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;
import com.talhanation.bannermod.war.config.WarServerConfig;
import com.talhanation.bannermod.war.cooldown.WarCooldownPolicy;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import com.talhanation.bannermod.war.rp.WarDeclarationFormatter;
import com.talhanation.bannermod.war.rp.WarNoticeService;
import com.talhanation.bannermod.war.runtime.ClaimRepublisher;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRuntime;
import com.talhanation.bannermod.war.runtime.WarGoalType;
import com.talhanation.bannermod.war.runtime.WarOutcomeApplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WarDeclarationCommands {
    private WarDeclarationCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("war")
                .then(Commands.literal("declare")
                        .then(Commands.argument("attacker", StringArgumentType.word())
                                .then(Commands.argument("defender", StringArgumentType.word())
                                        .then(Commands.argument("goal", StringArgumentType.word())
                                                .executes(ctx -> declare(ctx, ""))
                                                .then(Commands.argument("casusBelli", StringArgumentType.greedyString())
                                                        .executes(ctx -> declare(ctx,
                                                                StringArgumentType.getString(ctx, "casusBelli"))))))))
                .then(Commands.literal("info")
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .executes(WarDeclarationCommands::info)))
                .then(Commands.literal("list")
                        .executes(WarDeclarationCommands::list))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .executes(ctx -> resolve(ctx, ResolveMode.CANCEL, 0L))))
                .then(Commands.literal("whitepeace")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .executes(ctx -> resolve(ctx, ResolveMode.WHITE_PEACE, 0L))))
                .then(Commands.literal("tribute")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> resolve(ctx, ResolveMode.TRIBUTE,
                                                LongArgumentType.getLong(ctx, "amount"))))))
                .then(Commands.literal("vassalize")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .executes(WarDeclarationCommands::vassalize)))
                .then(Commands.literal("demilitarize")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                        .executes(WarDeclarationCommands::demilitarize))))
                .then(Commands.literal("occupy")
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .executes(ctx -> occupy(ctx, 0))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 8))
                                        .executes(ctx -> occupy(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("annex")
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .executes(WarDeclarationCommands::annex)))
                .then(Commands.literal("occupations")
                        .executes(WarDeclarationCommands::listOccupations))
                .then(Commands.literal("revolts")
                        .executes(WarDeclarationCommands::listRevolts))
                .then(Commands.literal("revolt")
                        .then(Commands.literal("resolve")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("revoltId", StringArgumentType.word())
                                        .then(Commands.argument("outcome", StringArgumentType.word())
                                                .executes(WarDeclarationCommands::revoltResolve)))));
    }

    private static int declare(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               String casusBelli) throws CommandSyntaxException {
        String attackerToken = StringArgumentType.getString(context, "attacker");
        String defenderToken = StringArgumentType.getString(context, "defender");
        String goalToken = StringArgumentType.getString(context, "goal");

        PoliticalEntityRecord attacker = WarCommandSupport.requireEntity(context, attackerToken);
        PoliticalEntityRecord defender = WarCommandSupport.requireEntity(context, defenderToken);
        if (!WarCommandSupport.isLeaderOrOp(context, attacker)) {
            throw WarCommandSupport.ERR_NOT_LEADER.create();
        }
        if (!attacker.status().canDeclareOffensiveWar()) {
            context.getSource().sendFailure(Component.literal(
                    "Attacker status " + attacker.status().name() + " cannot declare offensive war."));
            return 0;
        }
        WarGoalType goal;
        try {
            goal = WarGoalType.valueOf(goalToken.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            context.getSource().sendFailure(Component.literal("Unknown goal: " + goalToken));
            return 0;
        }

        ServerLevel level = WarCommandSupport.level(context);
        WarDeclarationRuntime declarations = WarRuntimeContext.declarations(level);
        long gameTime = level.getGameTime();
        long peaceCooldownTicks = WarServerConfig.peaceCooldownTicks();
        int defenderDailyLimit = WarServerConfig.DefenderDailyDeclarations.get();

        WarCooldownPolicy.Result cooldown = WarCooldownPolicy.canDeclareWithImmunity(
                attacker.id(), defender.id(),
                declarations.all(), gameTime, peaceCooldownTicks, defenderDailyLimit,
                WarRuntimeContext.demilitarizations(level),
                WarRuntimeContext.cooldowns(level));
        if (!cooldown.valid()) {
            context.getSource().sendFailure(Component.literal("Declaration blocked: " + cooldown.reason()));
            return 0;
        }

        long minDelay = WarServerConfig.MinDeclarationDelayTicks.get();
        Optional<WarDeclarationRecord> declared = declarations.declareWar(
                attacker.id(),
                defender.id(),
                goal,
                casusBelli,
                List.of(),
                List.of(),
                List.of(),
                gameTime,
                minDelay
        );
        if (declared.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Failed to declare war."));
            return 0;
        }

        WarAuditLogSavedData audit = WarRuntimeContext.audit(level);
        WarDeclarationRecord war = declared.get();
        audit.append(war.id(), "WAR_DECLARED",
                "attacker=" + attacker.id() + ";defender=" + defender.id() + ";goal=" + goal.name(),
                gameTime);

        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        WarNoticeService.broadcastDeclaration(context.getSource().getServer(), war, registry);
        WarCommandSupport.replyComponent(context,
                Component.literal("War declared: ").append(WarDeclarationFormatter.summary(war, registry)));
        return 1;
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "warId");
        WarDeclarationRecord war = WarCommandSupport.requireWar(context, token);
        PoliticalRegistryRuntime registry = WarCommandSupport.registry(context);
        WarCommandSupport.replyComponent(context, WarDeclarationFormatter.detail(war, registry));
        return 1;
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        WarDeclarationRuntime declarations = WarCommandSupport.declarations(context);
        PoliticalRegistryRuntime registry = WarCommandSupport.registry(context);
        if (declarations.all().isEmpty()) {
            WarCommandSupport.reply(context, "No war declarations.");
            return 0;
        }
        MutableComponent text = Component.literal("Wars:\n");
        for (WarDeclarationRecord war : declarations.all()) {
            text.append(WarDeclarationFormatter.summary(war, registry)).append(Component.literal("\n"));
        }
        WarCommandSupport.replyComponent(context, text);
        return declarations.all().size();
    }

    private static int resolve(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               ResolveMode mode,
                               long tributeAmount) throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "warId");
        WarDeclarationRecord war = WarCommandSupport.requireWar(context, token);
        ServerLevel level = WarCommandSupport.level(context);
        WarOutcomeApplier applier = WarRuntimeContext.applierFor(level);
        long gameTime = level.getGameTime();

        WarOutcomeApplier.Result result = switch (mode) {
            case CANCEL -> {
                PoliticalRegistryRuntime reg = WarRuntimeContext.registry(level);
                Optional<PoliticalEntityRecord> attacker = reg.byId(war.attackerPoliticalEntityId());
                if (!context.getSource().hasPermission(2)
                        && (attacker.isEmpty() || !WarCommandSupport.isLeaderOrOp(context, attacker.get()))) {
                    throw WarCommandSupport.ERR_NOT_LEADER.create();
                }
                yield applier.cancel(war.id(), gameTime, "command_cancel");
            }
            case WHITE_PEACE -> applier.applyWhitePeace(war.id(), gameTime);
            case TRIBUTE -> applier.applyTribute(war.id(), tributeAmount, gameTime);
            case OUTCOME -> WarOutcomeApplier.Result.invalid("invalid_resolve_mode");
        };

        return finalizeOutcome(context, level, war, mode, result);
    }

    private static int vassalize(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "warId");
        WarDeclarationRecord war = WarCommandSupport.requireWar(context, token);
        ServerLevel level = WarCommandSupport.level(context);
        WarOutcomeApplier applier = WarRuntimeContext.applierFor(level);
        WarOutcomeApplier.Result result = applier.applyVassalize(war.id(), level.getGameTime());
        return finalizeOutcome(context, level, war, ResolveMode.OUTCOME, result);
    }

    private static int demilitarize(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "warId");
        int days = IntegerArgumentType.getInteger(context, "days");
        WarDeclarationRecord war = WarCommandSupport.requireWar(context, token);
        ServerLevel level = WarCommandSupport.level(context);
        long ticks = (long) days * WarCooldownPolicy.TICKS_PER_DAY;
        WarOutcomeApplier applier = WarRuntimeContext.applierFor(level);
        WarOutcomeApplier.Result result = applier.applyDemilitarization(war.id(), ticks, level.getGameTime());
        return finalizeOutcome(context, level, war, ResolveMode.OUTCOME, result);
    }

    private static int occupy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, int radius)
            throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "warId");
        WarDeclarationRecord war = WarCommandSupport.requireWar(context, token);
        ServerLevel level = WarCommandSupport.level(context);
        PoliticalRegistryRuntime reg = WarRuntimeContext.registry(level);
        Optional<PoliticalEntityRecord> attacker = reg.byId(war.attackerPoliticalEntityId());
        if (!context.getSource().hasPermission(2)
                && (attacker.isEmpty() || !WarCommandSupport.isLeaderOrOp(context, attacker.get()))) {
            throw WarCommandSupport.ERR_NOT_LEADER.create();
        }
        ChunkPos centre = new ChunkPos(BlockPos.containing(context.getSource().getPosition()));
        int r = Math.max(0, Math.min(radius, 8));
        List<ChunkPos> chunks = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                chunks.add(new ChunkPos(centre.x + dx, centre.z + dz));
            }
        }
        WarOutcomeApplier applier = WarRuntimeContext.applierFor(level);
        WarOutcomeApplier.Result result = applier.applyOccupy(war.id(), chunks, level.getGameTime());
        return finalizeOutcome(context, level, war, ResolveMode.OUTCOME, result);
    }

    private static int annex(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, "warId");
        WarDeclarationRecord war = WarCommandSupport.requireWar(context, token);
        ServerLevel level = WarCommandSupport.level(context);
        PoliticalRegistryRuntime reg = WarRuntimeContext.registry(level);
        Optional<PoliticalEntityRecord> attacker = reg.byId(war.attackerPoliticalEntityId());
        if (!context.getSource().hasPermission(2)
                && (attacker.isEmpty() || !WarCommandSupport.isLeaderOrOp(context, attacker.get()))) {
            throw WarCommandSupport.ERR_NOT_LEADER.create();
        }
        ChunkPos centre = new ChunkPos(BlockPos.containing(context.getSource().getPosition()));
        WarOutcomeApplier applier = WarRuntimeContext.applierFor(level);
        RecruitsClaimManager claimManager = ClaimEvents.recruitsClaimManager;
        ClaimRepublisher publisher = c -> claimManager.addOrUpdateClaim(level, c);
        RecruitsClaim claim = claimManager.getClaim(centre);
        WarOutcomeApplier.Result result = applier.applyAnnex(war.id(), centre, level.getGameTime(), publisher);
        if (result.valid() && claim != null) {
            int rebound = WarAnnexEffects.rebindEntitiesToNewOwner(
                    level, claim,
                    war.defenderPoliticalEntityId(),
                    war.attackerPoliticalEntityId());
            WarRuntimeContext.audit(level).append(war.id(), "ANNEX_REBIND",
                    "claim=" + claim.getUUID() + ";rebound=" + rebound,
                    level.getGameTime());
        }
        return finalizeOutcome(context, level, war, ResolveMode.OUTCOME, result);
    }

    private static int finalizeOutcome(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                       ServerLevel level,
                                       WarDeclarationRecord war,
                                       ResolveMode mode,
                                       WarOutcomeApplier.Result result) {
        if (!result.valid()) {
            context.getSource().sendFailure(Component.literal("Outcome rejected: " + result.reason()));
            return 0;
        }
        WarDeclarationRuntime declarations = WarRuntimeContext.declarations(level);
        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        Optional<WarDeclarationRecord> updated = declarations.byId(war.id());
        WarDeclarationRecord finalWar = updated.orElse(war);
        if (mode == ResolveMode.CANCEL) {
            WarNoticeService.broadcastCancelled(context.getSource().getServer(), finalWar, registry);
            WarCommandSupport.reply(context, "War cancelled.");
        } else {
            String outcomeName = result.outcome() == null ? "RESOLVED" : result.outcome().name();
            WarNoticeService.broadcastOutcome(context.getSource().getServer(), finalWar, registry, outcomeName);
            WarCommandSupport.reply(context, "War resolved: " + outcomeName);
        }
        return 1;
    }

    private enum ResolveMode { CANCEL, WHITE_PEACE, TRIBUTE, OUTCOME }

    private static int listOccupations(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        ServerLevel level = WarCommandSupport.level(context);
        var occupations = WarRuntimeContext.occupations(level);
        if (occupations.all().isEmpty()) {
            WarCommandSupport.reply(context, "No active occupations.");
            return 0;
        }
        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        MutableComponent text = Component.literal("Occupations:\n");
        for (var record : occupations.all()) {
            String occupier = registry.byId(record.occupierEntityId())
                    .map(PoliticalEntityRecord::name).orElse("?");
            String occupied = registry.byId(record.occupiedEntityId())
                    .map(PoliticalEntityRecord::name).orElse("?");
            text.append(Component.literal(" id=" + record.id().toString().substring(0, 8)
                    + " war=" + record.warId().toString().substring(0, 8)
                    + " occupier=" + occupier
                    + " occupied=" + occupied
                    + " chunks=" + record.chunks().size() + "\n"));
        }
        WarCommandSupport.replyComponent(context, text);
        return occupations.all().size();
    }

    private static int listRevolts(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        ServerLevel level = WarCommandSupport.level(context);
        var revolts = WarRuntimeContext.revolts(level);
        if (revolts.all().isEmpty()) {
            WarCommandSupport.reply(context, "No revolts.");
            return 0;
        }
        PoliticalRegistryRuntime registry = WarRuntimeContext.registry(level);
        MutableComponent text = Component.literal("Revolts:\n");
        for (var record : revolts.all()) {
            String rebel = registry.byId(record.rebelEntityId())
                    .map(PoliticalEntityRecord::name).orElse("?");
            String occupier = registry.byId(record.occupierEntityId())
                    .map(PoliticalEntityRecord::name).orElse("?");
            text.append(Component.literal(" id=" + record.id().toString().substring(0, 8)
                    + " state=" + record.state().name()
                    + " rebel=" + rebel
                    + " occupier=" + occupier + "\n"));
        }
        WarCommandSupport.replyComponent(context, text);
        return revolts.all().size();
    }

    private static int revoltResolve(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String revoltToken = StringArgumentType.getString(context, "revoltId");
        String outcomeToken = StringArgumentType.getString(context, "outcome");
        ServerLevel level = WarCommandSupport.level(context);
        var revolts = WarRuntimeContext.revolts(level);
        Optional<com.talhanation.bannermod.war.runtime.RevoltRecord> revolt = revolts.byIdFragment(revoltToken);
        if (revolt.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Revolt not found."));
            return 0;
        }
        com.talhanation.bannermod.war.runtime.RevoltState newState;
        switch (outcomeToken.toLowerCase(java.util.Locale.ROOT)) {
            case "success" -> newState = com.talhanation.bannermod.war.runtime.RevoltState.SUCCESS;
            case "fail", "failed", "failure" ->
                    newState = com.talhanation.bannermod.war.runtime.RevoltState.FAILED;
            default -> {
                context.getSource().sendFailure(Component.literal("Outcome must be 'success' or 'fail'."));
                return 0;
            }
        }
        long gameTime = level.getGameTime();
        boolean resolved = revolts.resolve(revolt.get().id(), newState, gameTime);
        if (!resolved) {
            context.getSource().sendFailure(Component.literal("Failed to resolve revolt."));
            return 0;
        }
        WarOutcomeApplier applier = WarRuntimeContext.applierFor(level);
        if (newState == com.talhanation.bannermod.war.runtime.RevoltState.SUCCESS) {
            applier.removeOccupationOnRevoltSuccess(revolt.get().occupationId(), gameTime);
            WarCommandSupport.reply(context, "Revolt succeeded; occupation removed.");
        } else {
            WarRuntimeContext.audit(level).append(null, "REVOLT_FAILED",
                    "revolt=" + revolt.get().id() + ";occupation=" + revolt.get().occupationId(),
                    gameTime);
            WarCommandSupport.reply(context, "Revolt failed.");
        }
        return 1;
    }
}
