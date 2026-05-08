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
import com.talhanation.bannermod.persistence.military.RecruitsClaimSaveData;
import com.talhanation.bannermod.persistence.military.RecruitsGroupsSaveData;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.bootstrap.SettlementRegistryData;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRegistryData;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchSavedData;
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
