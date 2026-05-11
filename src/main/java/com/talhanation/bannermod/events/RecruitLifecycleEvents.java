package com.talhanation.bannermod.events;

import com.talhanation.bannermod.ai.pathfinding.AsyncPathProcessor;
import com.talhanation.bannermod.ai.pathfinding.async.TrueAsyncPathfindingRuntime;
import com.talhanation.bannermod.entity.military.RecruitIndex;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.entity.military.runtime.RecruitWorldLifecycleService;
import com.talhanation.bannermod.governance.runtime.RecruitGovernorWorkflow;
import com.talhanation.bannermod.util.FormationDimensionGuard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.UUID;

public class RecruitLifecycleEvents {
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        RecruitWorldLifecycleService.RecruitManagers managers = RecruitWorldLifecycleService.initializeManagers(event.getServer());
        RecruitEvents.installRuntime(event.getServer(), managers.playerUnitManager(), managers.groupsManager());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // ServerStartedEvent runs after levels exist, so the async path runtime can start safely.
        AsyncPathProcessor.start();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        saveRecruitManagersSafely();

        AsyncPathProcessor.shutdown();
        TrueAsyncPathfindingRuntime.instance().shutdown();
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        // LevelEvent.Save fires once per dimension; gate on overworld so we only do the SavedData
        // write once per autosave instead of three times (overworld+nether+end).
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        saveRecruitManagersSafely();
    }

    private static void saveRecruitManagersSafely() {
        MinecraftServer server = RecruitEvents.server();
        if (server == null) return;
        var playerUnitManager = RecruitEvents.playerUnitManager();
        var groupsManager = RecruitEvents.groupsManager();
        if (playerUnitManager == null || groupsManager == null) return;
        try {
            RecruitWorldLifecycleService.saveManagers(server, playerUnitManager, groupsManager);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger(RecruitLifecycleEvents.class)
                    .error("Failed to persist recruit managers during world save", t);
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            RecruitWorldLifecycleService.syncPlayerJoin(
                    serverPlayer,
                    RecruitEvents.playerUnitManager(),
                    RecruitEvents.groupsManager()
            );
            RecruitGovernorWorkflow.syncGovernorSnapshotsOnLogin(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onTeleportEvent(EntityTeleportEvent event) {
        RecruitWorldLifecycleService.teleportFollowingRecruits(event);
    }

    @SubscribeEvent
    public void onServerTick(LevelTickEvent.Post event) {
        RecruitWorldLifecycleService.tickLevel(event, RecruitEvents.RECRUIT_PATROL, RecruitEvents.PILLAGER_PATROL);
    }

    @SubscribeEvent
    public void onHorseJoinWorld(EntityJoinLevelEvent event) {
        RecruitWorldLifecycleService.ensureHorseGoal(event.getEntity());
    }

    /**
     * FORMATIONDIM-001: when a leader walks through a portal, every recruit they
     * own that is still in the source dimension is now an orphan from the
     * formation goal's perspective. Bump the cross-dimension orphan counter by
     * the cohort size so we can attribute orphan churn to leader transitions
     * rather than per-recruit tick cadence.
     */
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        UUID ownerUuid = serverPlayer.getUUID();
        ServerLevel destination = server.getLevel(event.getTo());
        int orphaned = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (level == destination) continue;
            orphaned += RecruitIndex.instance().countOwnedInLevel(level, ownerUuid);
        }
        FormationDimensionGuard.recordOrphanedGroup(orphaned);
    }
}
