package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.governance.BannerModGovernorHeartbeat;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementOrchestrator;
import com.talhanation.bannermod.settlement.SettlementService;
import com.talhanation.bannermod.util.AdaptiveRuntimeBudgets;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.server.level.ServerLevel;

public final class SettlementHeartbeatService {
    private static final int GOVERNOR_TICK_INTERVAL = 200;
    private static final int GOVERNOR_HEARTBEAT_BATCH_SIZE = 16;
    private static final int SETTLEMENT_REFRESH_BATCH_SIZE = 16;
    private static final int SETTLEMENT_ORCHESTRATOR_BATCH_SIZE = 16;
    private static final int GOVERNOR_STAGE_IDLE = 0;
    private static final int GOVERNOR_STAGE_HEARTBEAT = 1;
    private static final int GOVERNOR_STAGE_REFRESH = 2;
    private static final int GOVERNOR_STAGE_ORCHESTRATOR = 3;

    private int governorCounter;
    private int governorMaintenanceStage;
    private int governorMaintenanceCursor;

    public void reset() {
        governorCounter = 0;
        governorMaintenanceStage = GOVERNOR_STAGE_IDLE;
        governorMaintenanceCursor = 0;
    }

    public void tick(ServerLevel level) {
        governorCounter++;

        if(governorMaintenanceStage == GOVERNOR_STAGE_IDLE && governorCounter >= GOVERNOR_TICK_INTERVAL){
            governorCounter = 0;
            governorMaintenanceStage = GOVERNOR_STAGE_HEARTBEAT;
            governorMaintenanceCursor = 0;
        }

        if(governorMaintenanceStage != GOVERNOR_STAGE_IDLE){
            tickGovernorMaintenance(level);
        }
    }

    private void tickGovernorMaintenance(ServerLevel level) {
        BannerModGovernorManager governorManager = BannerModGovernorManager.get(level);
        SettlementManager settlementManager = SettlementManager.get(level);

        if (governorMaintenanceStage == GOVERNOR_STAGE_HEARTBEAT) {
            long startNanos = System.nanoTime();
            BannerModGovernorHeartbeat.BatchResult result = SettlementTreasuryDerivationService.runGovernorHeartbeatBatch(
                    level,
                    ClaimEvents.claimManager(),
                    governorManager,
                    governorMaintenanceCursor,
                    GOVERNOR_HEARTBEAT_BATCH_SIZE
            );
            recordGovernorMaintenanceBatch("claim_events.settlement_heartbeat.governor_batch", result, startNanos);
            advanceGovernorMaintenance(result.nextIndex(), result.completed(), GOVERNOR_STAGE_REFRESH);
            return;
        }

        if (governorMaintenanceStage == GOVERNOR_STAGE_REFRESH) {
            long startNanos = System.nanoTime();
            SettlementClaimBindingService.BatchResult result = SettlementService.refreshClaimsBatch(
                    level,
                    ClaimEvents.claimManager(),
                    settlementManager,
                    governorManager,
                    governorMaintenanceCursor,
                    SETTLEMENT_REFRESH_BATCH_SIZE
            );
            recordGovernorMaintenanceBatch("claim_events.settlement_heartbeat.refresh_batch", result.startIndex(), result.nextIndex(), result.totalItems(), result.completed(), startNanos);
            advanceGovernorMaintenance(result.nextIndex(), result.completed(), GOVERNOR_STAGE_ORCHESTRATOR);
            return;
        }

        if (governorMaintenanceStage == GOVERNOR_STAGE_ORCHESTRATOR) {
            long startNanos = System.nanoTime();
            SettlementOrchestrator.BatchResult result = SettlementOrchestrator.tickBatch(
                    level,
                    settlementManager,
                    governorManager,
                    governorMaintenanceCursor,
                    AdaptiveRuntimeBudgets.intBudget(
                            "settlement.orchestrator.batch",
                            SETTLEMENT_ORCHESTRATOR_BATCH_SIZE,
                            1
                    )
            );
            recordGovernorMaintenanceBatch("claim_events.settlement_heartbeat.orchestrator_batch", result.startIndex(), result.nextIndex(), result.totalItems(), result.completed(), startNanos);
            advanceGovernorMaintenance(result.nextIndex(), result.completed(), GOVERNOR_STAGE_IDLE);
        }
    }

    private static void recordGovernorMaintenanceBatch(String keyPrefix, BannerModGovernorHeartbeat.BatchResult result, long startNanos) {
        recordGovernorMaintenanceBatch(keyPrefix, result.startIndex(), result.nextIndex(), result.totalItems(), result.completed(), startNanos);
    }

    private static void recordGovernorMaintenanceBatch(String keyPrefix, int startIndex, int nextIndex, int totalItems, boolean completed, long startNanos) {
        RuntimeProfilingCounters.recordBatch(keyPrefix, Math.max(0, nextIndex - startIndex), totalItems, System.nanoTime() - startNanos, completed);
    }

    private void advanceGovernorMaintenance(int nextIndex, boolean complete, int nextStage) {
        if (complete) {
            governorMaintenanceStage = nextStage;
            governorMaintenanceCursor = 0;
            return;
        }
        governorMaintenanceCursor = nextIndex;
    }
}
