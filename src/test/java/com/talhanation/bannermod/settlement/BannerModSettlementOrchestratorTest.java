package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.household.GoHomeResidentGoal;
import com.talhanation.bannermod.settlement.job.JobExecutionContext;
import com.talhanation.bannermod.settlement.job.JobExecutionResult;
import com.talhanation.bannermod.settlement.job.JobHandler;
import com.talhanation.bannermod.settlement.job.JobHandlerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementOrchestratorTest {

    private static final UUID CLAIM = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID RESIDENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID HOME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID MARKET = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final long DAY_TICK = 6000L;
    private static final long NIGHT_TICK = 15000L;

    @Test
    void tickSnapshotComposesGrowthProjectsHomesSellerDispatchGoalsAndSkipsJobsOutsideWorkTask() {
        RecordingJobHandler handler = new RecordingJobHandler();
        JobHandlerRegistry registry = new JobHandlerRegistry();
        registry.register(handler);
        SettlementOrchestrator.LevelRuntimeState state = SettlementOrchestrator.detachedStateForTests(registry);
        SettlementSnapshot snapshot = settlementSnapshot(NIGHT_TICK, true);

        SettlementOrchestrator.tickSnapshot(state, snapshot, null, NIGHT_TICK);

        assertEquals(HOME, state.homeRuntime.homeFor(RESIDENT).orElseThrow().homeBuildingUuid());
        assertTrue(state.sellerRuntime.phase(RESIDENT).isPresent(), "ready seller seed should start a live dispatch");

        List<PendingProject> queuedProjects = state.projectRuntime.snapshot(CLAIM);
        assertFalse(queuedProjects.isEmpty(), "growth scoring should feed the project runtime queue");
        assertEquals(SettlementBuildingProfileSeed.GENERAL, queuedProjects.get(0).profileSeed());

        Optional<ResidentTask> task = state.goalScheduler.currentTask(RESIDENT);
        assertTrue(task.isPresent(), "resident should receive a scheduled task");
        assertEquals(GoHomeResidentGoal.ID, task.get().goalId());

        assertEquals(0, handler.invocationCount, "job execution should stay gated behind the work goal");
    }

    @Test
    void tickSnapshotRunsJobsDuringWorkGoalAndRespectsHandlerCooldown() {
        RecordingJobHandler handler = new RecordingJobHandler(20);
        JobHandlerRegistry registry = new JobHandlerRegistry();
        registry.register(handler);
        SettlementOrchestrator.LevelRuntimeState state = SettlementOrchestrator.detachedStateForTests(registry);
        SettlementSnapshot snapshot = settlementSnapshot(DAY_TICK, false);

        SettlementOrchestrator.tickSnapshot(state, snapshot, null, DAY_TICK);
        SettlementOrchestrator.tickSnapshot(state, snapshot, null, DAY_TICK + 5);
        SettlementOrchestrator.tickSnapshot(state, snapshot, null, DAY_TICK + 20);

        Optional<ResidentTask> task = state.goalScheduler.currentTask(RESIDENT);
        assertTrue(task.isPresent(), "resident should receive a scheduled task");
        assertEquals(WorkResidentGoal.ID, task.get().goalId());
        assertEquals(2, handler.invocationCount, "cooldown should block intermediate job ticks");
        assertEquals(RESIDENT, handler.lastResidentUuid);
        assertEquals(MARKET, handler.lastWorkplaceUuid);
    }

    @Test
    void tickSnapshotCancelsStaleLiveDispatchesAndRebindsSellerToCurrentSeed() {
        SettlementOrchestrator.LevelRuntimeState state = SettlementOrchestrator.detachedStateForTests(JobHandlerRegistry.defaults());

        SettlementOrchestrator.tickSnapshot(state, settlementSnapshot(NIGHT_TICK, true), null, NIGHT_TICK);

        UUID otherMarket = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        SettlementMarketState reboundMarketState = new SettlementMarketState(
                1,
                1,
                16,
                8,
                1,
                1,
                List.of(new SettlementMarketRecord(otherMarket, "Other Market", true, 16, 8)),
                List.of(new SettlementSellerDispatchRecord(
                        RESIDENT,
                        otherMarket,
                        "Other Market",
                        SettlementSellerDispatchState.READY
                ))
        );
        SettlementSnapshot reboundSnapshot = new SettlementSnapshot(
                CLAIM,
                0,
                0,
                "teamA",
                NIGHT_TICK + 1,
                1,
                1,
                1,
                1,
                1,
                0,
                SettlementStockpileSummary.empty(),
                reboundMarketState,
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                settlementSnapshot(NIGHT_TICK, true).residents(),
                settlementSnapshot(NIGHT_TICK, true).buildings()
        );

        SettlementOrchestrator.tickSnapshot(state, reboundSnapshot, null, NIGHT_TICK + 1);

        assertEquals(otherMarket, state.sellerRuntime.phase(RESIDENT).orElseThrow().marketRecordUuid());
        assertEquals(com.talhanation.bannermod.settlement.dispatch.SellerPhase.MOVING_TO_STALL, state.sellerRuntime.phase(RESIDENT).orElseThrow().phase());
    }

    @Test
    void tickSnapshotFeedsReservationAwareHintsIntoGrowthQueue() {
        SettlementOrchestrator.LevelRuntimeState state = SettlementOrchestrator.detachedStateForTests(JobHandlerRegistry.defaults());
        SettlementSnapshot base = settlementSnapshot(NIGHT_TICK, true);
        SettlementSnapshot hintedSnapshot = new SettlementSnapshot(
                base.claimUuid(),
                0,
                0,
                "teamA",
                NIGHT_TICK,
                2,
                1,
                1,
                1,
                0,
                0,
                SettlementStockpileSummary.empty(),
                base.marketState(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                new SettlementTradeRouteHandoffSnapshot(
                        1,
                        1,
                        0,
                        0,
                        2,
                        12,
                        List.of(new SettlementDesiredGoodSnapshot("market_goods", 0)),
                        List.of(),
                        List.of()
                ),
                new SettlementSupplySignalState(
                        1,
                        0,
                        0,
                        8,
                        List.of(new SettlementSupplySignal("market_goods", 0, 0, 0, 8))
                ),
                base.residents(),
                base.buildings()
        );

        SettlementOrchestrator.tickSnapshot(state, hintedSnapshot, null, NIGHT_TICK);

        List<PendingProject> queuedProjects = state.projectRuntime.snapshot(CLAIM);
        assertFalse(queuedProjects.isEmpty(), "reservation-aware hint snapshot should drive live project scoring");
        assertEquals(SettlementBuildingProfileSeed.MARKET, queuedProjects.get(0).profileSeed());
    }

    @Test
    void batchSnapshotOrderIsSortedOncePerMaintenanceCycle() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        SettlementManager manager = new SettlementManager();
        SettlementSnapshot base = settlementSnapshot(DAY_TICK, false);
        manager.putSnapshot(withClaim(base, third));
        manager.putSnapshot(withClaim(base, first));
        SettlementOrchestrator.LevelRuntimeState state = SettlementOrchestrator.detachedStateForTests(JobHandlerRegistry.defaults());

        List<UUID> firstBatchOrder = state.snapshotOrderForBatch(manager, 0);
        manager.putSnapshot(withClaim(base, second));
        List<UUID> secondBatchOrder = state.snapshotOrderForBatch(manager, 1);

        assertEquals(List.of(first, third), firstBatchOrder);
        assertSame(firstBatchOrder, secondBatchOrder, "later batches in the same cycle should reuse the cached order");
        List<UUID> nextCycleOrder = state.snapshotOrderForBatch(manager, 0);
        assertEquals(List.of(first, second, third), nextCycleOrder);
    }

    @Test
    void claimTickServicePreservesSnapshotTickComposition() {
        RecordingJobHandler handler = new RecordingJobHandler();
        JobHandlerRegistry registry = new JobHandlerRegistry();
        registry.register(handler);
        SettlementOrchestrator.LevelRuntimeState state = SettlementOrchestrator.detachedStateForTests(registry);

        SettlementClaimTickService.tickSnapshot(state, settlementSnapshot(NIGHT_TICK, true), null, null, NIGHT_TICK);

        assertEquals(HOME, state.homeRuntime.homeFor(RESIDENT).orElseThrow().homeBuildingUuid());
        assertTrue(state.sellerRuntime.phase(RESIDENT).isPresent());
        assertFalse(state.projectRuntime.snapshot(CLAIM).isEmpty());
    }

    private static SettlementSnapshot settlementSnapshot(long gameTime, boolean includeSellerDispatch) {
        SettlementResidentServiceContract serviceContract = new SettlementResidentServiceContract(
                SettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                MARKET,
                "market_area"
        );
        SettlementResidentRecord resident = new SettlementResidentRecord(
                RESIDENT,
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                serviceContract,
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
                "teamA",
                MARKET,
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );

        SettlementBuildingRecord home = new SettlementBuildingRecord(
                HOME,
                "house",
                BlockPos.ZERO,
                null,
                null,
                2,
                0,
                0,
                List.of()
        );
        SettlementBuildingRecord market = new SettlementBuildingRecord(
                MARKET,
                "market_area",
                new BlockPos(4, 64, 4),
                null,
                null,
                0,
                1,
                1,
                List.of()
        );

        SettlementMarketState marketState = new SettlementMarketState(
                1,
                1,
                16,
                8,
                1,
                1,
                List.of(new SettlementMarketRecord(MARKET, "Market", true, 16, 8)),
                includeSellerDispatch
                        ? List.of(new SettlementSellerDispatchRecord(
                        RESIDENT,
                        MARKET,
                        "Market",
                        SettlementSellerDispatchState.READY
                ))
                        : List.of()
        );

        return new SettlementSnapshot(
                CLAIM,
                0,
                0,
                "teamA",
                gameTime,
                1,
                1,
                1,
                1,
                1,
                0,
                SettlementStockpileSummary.empty(),
                marketState,
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(resident),
                List.of(home, market)
        );
    }

    private static SettlementSnapshot withClaim(SettlementSnapshot base, UUID claimUuid) {
        return new SettlementSnapshot(
                claimUuid,
                base.anchorChunkX(),
                base.anchorChunkZ(),
                base.settlementFactionId(),
                base.lastRefreshedTick(),
                base.residentCapacity(),
                base.workplaceCapacity(),
                base.assignedWorkerCount(),
                base.assignedResidentCount(),
                base.unassignedWorkerCount(),
                base.missingWorkAreaAssignmentCount(),
                base.stockpileSummary(),
                base.marketState(),
                base.desiredGoodsSnapshot(),
                base.projectCandidateSnapshot(),
                base.tradeRouteHandoffSnapshot(),
                base.supplySignalState(),
                base.residents(),
                base.buildings()
        );
    }

    private static final class RecordingJobHandler implements JobHandler {
        private final int cooldownTicks;
        int invocationCount;
        UUID lastResidentUuid;
        UUID lastWorkplaceUuid;

        private RecordingJobHandler() {
            this(0);
        }

        private RecordingJobHandler(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
        }

        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("bannermod", "test_orchestrator_job");
        }

        @Override
        public SettlementJobHandlerSeed handles() {
            return SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR;
        }

        @Override
        public boolean canHandle(JobExecutionContext ctx) {
            return true;
        }

        @Override
        public JobExecutionResult runOneStep(JobExecutionContext ctx) {
            this.invocationCount++;
            this.lastResidentUuid = ctx.resident().residentUuid();
            this.lastWorkplaceUuid = ctx.workplace().orElse(null);
            return JobExecutionResult.COMPLETED;
        }

        @Override
        public int cooldownTicks() {
            return this.cooldownTicks;
        }
    }
}
