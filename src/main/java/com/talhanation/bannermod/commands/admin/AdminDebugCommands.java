package com.talhanation.bannermod.commands.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.talhanation.bannermod.ai.pathfinding.GlobalPathfindingController;
import com.talhanation.bannermod.entity.civilian.WorkerIndex;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitPlayerUnitSaveData;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimSaveData;
import com.talhanation.bannermod.persistence.military.RecruitsGroupsSaveData;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementOrchestrator;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.bootstrap.SettlementRegistryData;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRegistryData;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchSavedData;
import com.talhanation.bannermod.settlement.economy.ClaimStrategicEconomySummary;
import com.talhanation.bannermod.settlement.economy.ClaimStrategicEconomySummaryService;
import com.talhanation.bannermod.settlement.economy.FortLevelDefinition;
import com.talhanation.bannermod.settlement.economy.NpcDemandContractSavedData;
import com.talhanation.bannermod.settlement.economy.NpcDemandContractService;
import com.talhanation.bannermod.settlement.economy.StrategicMineSite;
import com.talhanation.bannermod.settlement.economy.StrategicMineSiteService;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentSavedData;
import com.talhanation.bannermod.settlement.prefab.player.PlayerBuildingRegistrySavedData;
import com.talhanation.bannermod.settlement.project.SettlementProjectSavedData;
import com.talhanation.bannermod.settlement.validation.BuildingInvalidationQueueData;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderSavedData;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionSavedData;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;
import com.talhanation.bannermod.war.cooldown.WarCooldownSavedData;
import com.talhanation.bannermod.war.registry.WarPoliticalRegistrySavedData;
import com.talhanation.bannermod.war.runtime.DemilitarizationSavedData;
import com.talhanation.bannermod.war.runtime.OccupationSavedData;
import com.talhanation.bannermod.war.runtime.RevoltSavedData;
import com.talhanation.bannermod.war.runtime.SiegeStandardSavedData;
import com.talhanation.bannermod.war.runtime.WarAllyInviteSavedData;
import com.talhanation.bannermod.war.runtime.WarDeclarationSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDebugCommands {
    private static final SimpleCommandExceptionType INVALID_CHUNK = new SimpleCommandExceptionType(
            Component.literal("chunk must be formatted as x,z")
    );
    private static final SimpleCommandExceptionType SERVER_ONLY = new SimpleCommandExceptionType(
            Component.literal("This command can only run on a server")
    );
    private static final List<Class<?>> VERSIONED_SAVED_DATA = List.of(
            RecruitsClaimSaveData.class,
            RecruitsGroupsSaveData.class,
            RecruitPlayerUnitSaveData.class,
            BannerModSeaTradeExecutionSavedData.class,
            WarPoliticalRegistrySavedData.class,
            WarCooldownSavedData.class,
            WarAllyInviteSavedData.class,
            OccupationSavedData.class,
            RevoltSavedData.class,
            DemilitarizationSavedData.class,
            SiegeStandardSavedData.class,
            WarDeclarationSavedData.class,
            WarAuditLogSavedData.class,
            BannerModGovernorManager.class,
            BannerModTreasuryManager.class,
            BannerModSellerDispatchSavedData.class,
            SettlementWorkOrderSavedData.class,
            BannerModHomeAssignmentSavedData.class,
            SettlementManager.class,
            SettlementRegistryData.class,
            BuildingInvalidationQueueData.class,
            ValidatedBuildingRegistryData.class,
            SettlementProjectSavedData.class,
            NpcDemandContractSavedData.class,
            PlayerBuildingRegistrySavedData.class
    );

    private AdminDebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> debug() {
        return Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("index")
                        .then(indexTarget("recruits"))
                        .then(indexTarget("workers"))
                        .then(indexTarget("workareas")))
                .then(Commands.literal("pathfinding")
                        .then(Commands.literal("stats")
                                .executes(AdminDebugCommands::pathfindingStats)))
                .then(Commands.literal("counters")
                        .then(Commands.literal("dump")
                                .executes(AdminDebugCommands::countersDump)))
                .then(Commands.literal("economy")
                        .then(Commands.literal("claim")
                                .then(Commands.argument("claimUuid", StringArgumentType.word())
                                        .executes(AdminDebugCommands::claimEconomySummary)))
                        .then(Commands.literal("mines")
                                .then(Commands.argument("claimUuid", StringArgumentType.word())
                                        .executes(AdminDebugCommands::claimMineSites)))
                        .then(Commands.literal("contracts")
                                .then(Commands.argument("claimUuid", StringArgumentType.word())
                                        .executes(AdminDebugCommands::claimContracts))))
                .then(Commands.literal("save-versions")
                        .executes(AdminDebugCommands::saveVersions));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> indexTarget(String type) {
        return Commands.literal(type)
                .then(Commands.argument("chunk", StringArgumentType.greedyString())
                        .executes(context -> index(context, type)));
    }

    private static int index(CommandContext<CommandSourceStack> context, String type) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        ChunkPos chunk = parseChunk(StringArgumentType.getString(context, "chunk"));
        int count = switch (type) {
            case "recruits" -> RecruitIndex.instance().countInChunk(level, chunk, true);
            case "workers" -> WorkerIndex.instance().countInChunk(level, chunk, true);
            case "workareas" -> WorkAreaIndex.instance().countInChunk(level, chunk, AbstractWorkAreaEntity.class);
            default -> throw new IllegalArgumentException("Unknown index type: " + type);
        };
        context.getSource().sendSuccess(() -> Component.literal(
                "Index " + type + " chunk " + chunk.x + "," + chunk.z + ": " + count
        ), false);
        return count;
    }

    private static int pathfindingStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        serverLevel(context.getSource());
        GlobalPathfindingController.ProfilingSnapshot snapshot = GlobalPathfindingController.profilingSnapshot();
        context.getSource().sendSuccess(() -> Component.literal(
                "Pathfinding stats: requests=" + snapshot.totalRequests()
                        + " executedBudget=" + snapshot.budgetUsedThisTick() + "/" + snapshot.requestBudgetPerTick()
                        + " deferredQueue=" + snapshot.currentDeferredQueueDepth()
                        + " maxDeferredQueue=" + snapshot.maxDeferredQueueDepth()
        ), false);
        return 1;
    }

    private static int countersDump(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        serverLevel(context.getSource());
        Map<String, Long> snapshot = RuntimeProfilingCounters.snapshot();
        if (snapshot.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No runtime counters recorded."), false);
            return 1;
        }
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            context.getSource().sendSuccess(() -> Component.literal(entry.getKey() + "=" + entry.getValue()), false);
        }
        return snapshot.size();
    }

    private static int saveVersions(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        serverLevel(context.getSource());
        int reported = 0;
        for (Class<?> savedDataClass : VERSIONED_SAVED_DATA) {
            Integer version = currentVersion(savedDataClass);
            if (version == null) {
                context.getSource().sendSuccess(() -> Component.literal(savedDataClass.getSimpleName() + "=unavailable"), false);
                continue;
            }
            reported++;
            context.getSource().sendSuccess(() -> Component.literal(savedDataClass.getSimpleName() + "=" + version), false);
        }
        return reported;
    }

    private static int claimEconomySummary(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        UUID claimUuid;
        try {
            claimUuid = UUID.fromString(StringArgumentType.getString(context, "claimUuid"));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("claimUuid must be a UUID"));
            return 0;
        }
        SettlementSnapshot snapshot = SettlementManager.get(level).getSnapshot(claimUuid);
        if (snapshot == null) {
            context.getSource().sendFailure(Component.literal("No settlement snapshot for claim " + claimUuid));
            return 0;
        }
        List<ValidatedBuildingRecord> validatedBuildings = ValidatedBuildingRegistryData.get(level).allRecords().stream()
                .filter(record -> record.settlementId().equals(claimUuid))
                .toList();
        RecruitsClaim claim = RecruitsClaimSaveData.get(level).getAllClaims().stream()
                .filter(candidate -> candidate.getUUID().equals(claimUuid))
                .findFirst()
                .orElse(null);
        List<StrategicMineSite> mineSites = claim == null
                ? List.of()
                : StrategicMineSiteService.derive(level, claim, snapshot, validatedBuildings);
        ClaimStrategicEconomySummary summary = ClaimStrategicEconomySummaryService.derive(
                snapshot,
                validatedBuildings,
                BannerModTreasuryManager.get(level).getLedger(claimUuid),
                mineSites,
                claim == null ? FortLevelDefinition.MIN_LEVEL : claim.getFortLevel()
        );
        for (String line : summary.debugLines()) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return summary.resources().size();
    }

    private static int claimMineSites(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = serverLevel(context.getSource());
        UUID claimUuid;
        try {
            claimUuid = UUID.fromString(StringArgumentType.getString(context, "claimUuid"));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("claimUuid must be a UUID"));
            return 0;
        }

        RecruitsClaim claim = RecruitsClaimSaveData.get(level).getAllClaims().stream()
                .filter(candidate -> candidate.getUUID().equals(claimUuid))
                .findFirst()
                .orElse(null);
        if (claim == null) {
            context.getSource().sendFailure(Component.literal("No claim " + claimUuid));
            return 0;
        }

        SettlementSnapshot snapshot = SettlementManager.get(level).getSnapshot(claimUuid);
        List<ValidatedBuildingRecord> validatedBuildings = ValidatedBuildingRegistryData.get(level).allRecords().stream()
                .filter(record -> record.settlementId().equals(claimUuid))
                .toList();
        List<StrategicMineSite> sites = StrategicMineSiteService.derive(level, claim, snapshot, validatedBuildings);
        if (sites.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No strategic mine sites for claim " + claimUuid), false);
            return 0;
        }
        for (StrategicMineSite site : sites) {
            context.getSource().sendSuccess(() -> Component.literal(site.debugLine()), false);
        }
        return sites.size();
    }

    private static int claimContracts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

    private static Integer currentVersion(Class<?> savedDataClass) {
        try {
            Field field = savedDataClass.getDeclaredField("CURRENT_VERSION");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static ChunkPos parseChunk(String value) throws CommandSyntaxException {
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw INVALID_CHUNK.create();
        }
        try {
            return new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            throw INVALID_CHUNK.create();
        }
    }

    private static ServerLevel serverLevel(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (level == null || level.isClientSide()) {
            throw SERVER_ONLY.create();
        }
        return level;
    }
}
