package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchSavedData;
import com.talhanation.bannermod.settlement.economy.NpcDemandContractSavedData;
import com.talhanation.bannermod.settlement.economy.NpcDemandContractService;
import com.talhanation.bannermod.settlement.goal.BannerModResidentGoalScheduler;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentRuntime;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentSavedData;
import com.talhanation.bannermod.settlement.job.JobHandlerRegistry;
import com.talhanation.bannermod.settlement.project.SettlementProjectRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublisherRegistry;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderSavedData;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SettlementOrchestrator {
    private static final WeakHashMap<ServerLevel, LevelRuntimeState> PER_LEVEL = new WeakHashMap<>();

    private SettlementOrchestrator() {
    }

    public static void tick(ServerLevel level,
                            SettlementManager settlementManager,
                            @Nullable BannerModGovernorManager governorManager) {
        tickBatch(level, settlementManager, governorManager, 0, Integer.MAX_VALUE);
    }

    public static BatchResult tickBatch(ServerLevel level,
                                        SettlementManager settlementManager,
                                        @Nullable BannerModGovernorManager governorManager,
                                        int startIndex,
                                        int maxSnapshots) {
        if (level == null || settlementManager == null) {
            return BatchResult.completedResult();
        }
        long startNanos = System.nanoTime();
        LevelRuntimeState state = runtimeState(level);
        long gameTime = level.getGameTime();
        state.workOrderRuntime.reclaimAbandoned(gameTime);
        List<UUID> snapshotOrder = state.snapshotOrderForBatch(settlementManager, startIndex);
        int total = snapshotOrder.size();
        if (total == 0 || maxSnapshots <= 0) {
            return recordBatchResult("settlement.heartbeat.orchestrator_batch", new BatchResult(0, total == 0 ? 0 : Math.max(0, Math.min(startIndex, total)), total, total == 0), startNanos);
        }

        int clampedStart = Math.max(0, Math.min(startIndex, total));
        int endIndex = Math.min(total, clampedStart + maxSnapshots);
        for (int i = clampedStart; i < endIndex; i++) {
            SettlementSnapshot snapshot = settlementManager.getSnapshot(snapshotOrder.get(i));
            if (snapshot == null) {
                continue;
            }
            BannerModGovernorSnapshot governorSnapshot = governorManager == null
                    ? null
                    : governorManager.getSnapshot(snapshot.claimUuid());
            tickSnapshot(state, snapshot, governorSnapshot, level, gameTime);
        }
        return recordBatchResult("settlement.heartbeat.orchestrator_batch", new BatchResult(clampedStart, endIndex, total, endIndex >= total), startNanos);
    }

    private static BatchResult recordBatchResult(String keyPrefix, BatchResult result, long startNanos) {
        RuntimeProfilingCounters.recordBatch(keyPrefix, Math.max(0, result.nextIndex() - result.startIndex()), result.totalItems(), System.nanoTime() - startNanos, result.completed());
        return result;
    }

    public record BatchResult(int startIndex,
                              int nextIndex,
                              int totalItems,
                              boolean completed) {
        private static BatchResult completedResult() {
            return new BatchResult(0, 0, 0, true);
        }
    }

    /** Accessor for the per-level work-order runtime, used by worker AI goals. */
    public static SettlementWorkOrderRuntime workOrderRuntime(ServerLevel level) {
        if (level == null) {
            return null;
        }
        return runtimeState(level).workOrderRuntime;
    }

    public static NpcDemandContractService npcDemandContractService(ServerLevel level) {
        if (level == null) {
            return null;
        }
        return runtimeState(level).npcDemandContractService;
    }

    static LevelRuntimeState detachedStateForTests(JobHandlerRegistry jobHandlerRegistry) {
        return LevelRuntimeState.create(SettlementProjectRuntime.detachedForTests(), jobHandlerRegistry);
    }

    static void tickSnapshot(LevelRuntimeState state,
                             SettlementSnapshot snapshot,
                             @Nullable BannerModGovernorSnapshot governorSnapshot,
                             long gameTime) {
        tickSnapshot(state, snapshot, governorSnapshot, null, gameTime);
    }

    static void tickSnapshot(LevelRuntimeState state,
                             SettlementSnapshot snapshot,
                             @Nullable BannerModGovernorSnapshot governorSnapshot,
                             @Nullable ServerLevel level,
                             long gameTime) {
        SettlementClaimTickService.tickSnapshot(state, snapshot, governorSnapshot, level, gameTime);
    }

    private static synchronized LevelRuntimeState runtimeState(ServerLevel level) {
        return PER_LEVEL.computeIfAbsent(level,
                ignored -> LevelRuntimeState.create(
                        SettlementProjectRuntime.forServer(level),
                        JobHandlerRegistry.defaults(),
                        SettlementWorkOrderSavedData.get(level).runtime(),
                        BannerModHomeAssignmentSavedData.get(level).runtime(),
                        BannerModSellerDispatchSavedData.get(level).runtime(),
                        NpcDemandContractSavedData.get(level).service()
                ));
    }

    static final class LevelRuntimeState {
        final SettlementProjectRuntime projectRuntime;
        final BannerModHomeAssignmentRuntime homeRuntime;
        final BannerModSellerDispatchRuntime sellerRuntime;
        final MutableMarketStateSupplier marketStateSupplier;
        final BannerModResidentGoalScheduler goalScheduler;
        final JobHandlerRegistry jobHandlerRegistry;
        final Map<UUID, Long> jobCooldownExpiries;
        final SettlementWorkOrderRuntime workOrderRuntime;
        final SettlementWorkOrderPublisherRegistry publisherRegistry;
        final NpcDemandContractService npcDemandContractService;
        private final List<UUID> orchestratorSnapshotOrder = new ArrayList<>();

        private LevelRuntimeState(SettlementProjectRuntime projectRuntime,
                                  BannerModHomeAssignmentRuntime homeRuntime,
                                  BannerModSellerDispatchRuntime sellerRuntime,
                                  MutableMarketStateSupplier marketStateSupplier,
                                  BannerModResidentGoalScheduler goalScheduler,
                                  JobHandlerRegistry jobHandlerRegistry,
                                  Map<UUID, Long> jobCooldownExpiries,
                                  SettlementWorkOrderRuntime workOrderRuntime,
                                  SettlementWorkOrderPublisherRegistry publisherRegistry,
                                  NpcDemandContractService npcDemandContractService) {
            this.projectRuntime = projectRuntime;
            this.homeRuntime = homeRuntime;
            this.sellerRuntime = sellerRuntime;
            this.marketStateSupplier = marketStateSupplier;
            this.goalScheduler = goalScheduler;
            this.jobHandlerRegistry = jobHandlerRegistry;
            this.jobCooldownExpiries = jobCooldownExpiries;
            this.workOrderRuntime = workOrderRuntime;
            this.publisherRegistry = publisherRegistry;
            this.npcDemandContractService = npcDemandContractService;
        }

        List<UUID> snapshotOrderForBatch(SettlementManager settlementManager, int startIndex) {
            if (startIndex <= 0 || this.orchestratorSnapshotOrder.isEmpty()) {
                this.orchestratorSnapshotOrder.clear();
                for (SettlementSnapshot snapshot : settlementManager.getAllSnapshots()) {
                    if (snapshot != null && snapshot.claimUuid() != null) {
                        this.orchestratorSnapshotOrder.add(snapshot.claimUuid());
                    }
                }
                this.orchestratorSnapshotOrder.sort(Comparator.naturalOrder());
            }
            return this.orchestratorSnapshotOrder;
        }

        private static LevelRuntimeState create(SettlementProjectRuntime projectRuntime,
                                                  JobHandlerRegistry jobHandlerRegistry) {
            return create(projectRuntime, jobHandlerRegistry, new SettlementWorkOrderRuntime(),
                    new BannerModHomeAssignmentRuntime(), new BannerModSellerDispatchRuntime());
        }

        private static LevelRuntimeState create(SettlementProjectRuntime projectRuntime,
                                                JobHandlerRegistry jobHandlerRegistry,
                                                SettlementWorkOrderRuntime workOrderRuntime) {
            return create(projectRuntime, jobHandlerRegistry, workOrderRuntime,
                    new BannerModHomeAssignmentRuntime(), new BannerModSellerDispatchRuntime());
        }

        private static LevelRuntimeState create(SettlementProjectRuntime projectRuntime,
                                                 JobHandlerRegistry jobHandlerRegistry,
                                                 SettlementWorkOrderRuntime workOrderRuntime,
                                                 BannerModHomeAssignmentRuntime homeRuntime,
                                                 BannerModSellerDispatchRuntime sellerRuntime) {
            return create(projectRuntime, jobHandlerRegistry, workOrderRuntime, homeRuntime, sellerRuntime, new NpcDemandContractService());
        }

        private static LevelRuntimeState create(SettlementProjectRuntime projectRuntime,
                                                JobHandlerRegistry jobHandlerRegistry,
                                                SettlementWorkOrderRuntime workOrderRuntime,
                                                BannerModHomeAssignmentRuntime homeRuntime,
                                                BannerModSellerDispatchRuntime sellerRuntime,
                                                NpcDemandContractService npcDemandContractService) {
            BannerModHomeAssignmentRuntime effectiveHomeRuntime = homeRuntime == null
                    ? new BannerModHomeAssignmentRuntime()
                    : homeRuntime;
            BannerModSellerDispatchRuntime effectiveSellerRuntime = sellerRuntime == null
                    ? new BannerModSellerDispatchRuntime()
                    : sellerRuntime;
            MutableMarketStateSupplier marketStateSupplier = new MutableMarketStateSupplier();
            return new LevelRuntimeState(
                    projectRuntime,
                    effectiveHomeRuntime,
                    effectiveSellerRuntime,
                    marketStateSupplier,
                    BannerModResidentGoalScheduler.withDefaultGoals(effectiveHomeRuntime, marketStateSupplier, effectiveSellerRuntime),
                    jobHandlerRegistry == null ? JobHandlerRegistry.defaults() : jobHandlerRegistry,
                    new HashMap<>(),
                    workOrderRuntime == null ? new SettlementWorkOrderRuntime() : workOrderRuntime,
                    SettlementWorkOrderPublisherRegistry.defaults(),
                    npcDemandContractService == null ? new NpcDemandContractService() : npcDemandContractService
            );
        }
    }

    static final class MutableMarketStateSupplier implements java.util.function.Supplier<SettlementMarketState> {
        private SettlementMarketState marketState = SettlementMarketState.empty();

        @Override
        public SettlementMarketState get() {
            return this.marketState;
        }

        void set(@Nullable SettlementMarketState marketState) {
            this.marketState = marketState == null ? SettlementMarketState.empty() : marketState;
        }
    }
}
