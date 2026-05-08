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

class BannerModSettlementOrchestratorTest {

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
        BannerModSettlementOrchestrator.LevelRuntimeState state = BannerModSettlementOrchestrator.detachedStateForTests(registry);
        BannerModSettlementSnapshot snapshot = settlementSnapshot(NIGHT_TICK, true);

        BannerModSettlementOrchestrator.tickSnapshot(state, snapshot, null, NIGHT_TICK);

        assertEquals(HOME, state.homeRuntime.homeFor(RESIDENT).orElseThrow().homeBuildingUuid());
        assertTrue(state.sellerRuntime.phase(RESIDENT).isPresent(), "ready seller seed should start a live dispatch");

        List<PendingProject> queuedProjects = state.projectRuntime.snapshot(CLAIM);
        assertFalse(queuedProjects.isEmpty(), "growth scoring should feed the project runtime queue");
        assertEquals(BannerModSettlementBuildingProfileSeed.GENERAL, queuedProjects.get(0).profileSeed());

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
        BannerModSettlementOrchestrator.LevelRuntimeState state = BannerModSettlementOrchestrator.detachedStateForTests(registry);
        BannerModSettlementSnapshot snapshot = settlementSnapshot(DAY_TICK, false);

        BannerModSettlementOrchestrator.tickSnapshot(state, snapshot, null, DAY_TICK);
        BannerModSettlementOrchestrator.tickSnapshot(state, snapshot, null, DAY_TICK + 5);
        BannerModSettlementOrchestrator.tickSnapshot(state, snapshot, null, DAY_TICK + 20);

        Optional<ResidentTask> task = state.goalScheduler.currentTask(RESIDENT);
        assertTrue(task.isPresent(), "resident should receive a scheduled task");
        assertEquals(WorkResidentGoal.ID, task.get().goalId());
        assertEquals(2, handler.invocationCount, "cooldown should block intermediate job ticks");
        assertEquals(RESIDENT, handler.lastResidentUuid);
        assertEquals(MARKET, handler.lastWorkplaceUuid);
    }

    @Test
    void tickSnapshotCancelsStaleLiveDispatchesAndRebindsSellerToCurrentSeed() {
        BannerModSettlementOrchestrator.LevelRuntimeState state = BannerModSettlementOrchestrator.detachedStateForTests(JobHandlerRegistry.defaults());

        BannerModSettlementOrchestrator.tickSnapshot(state, settlementSnapshot(NIGHT_TICK, true), null, NIGHT_TICK);

        UUID otherMarket = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        BannerModSettlementMarketState reboundMarketState = new BannerModSettlementMarketState(
                1,
                1,
                16,
                8,
                1,
                1,
                List.of(new BannerModSettlementMarketRecord(otherMarket, "Other Market", true, 16, 8)),
                List.of(new BannerModSettlementSellerDispatchRecord(
                        RESIDENT,
                        otherMarket,
                        "Other Market",
                        BannerModSettlementSellerDispatchState.READY
                ))
        );
        BannerModSettlementSnapshot reboundSnapshot = new BannerModSettlementSnapshot(
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
                BannerModSettlementStockpileSummary.empty(),
                reboundMarketState,
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                settlementSnapshot(NIGHT_TICK, true).residents(),
                settlementSnapshot(NIGHT_TICK, true).buildings()
        );

        BannerModSettlementOrchestrator.tickSnapshot(state, reboundSnapshot, null, NIGHT_TICK + 1);

        assertEquals(otherMarket, state.sellerRuntime.phase(RESIDENT).orElseThrow().marketRecordUuid());
        assertEquals(com.talhanation.bannermod.settlement.dispatch.SellerPhase.MOVING_TO_STALL, state.sellerRuntime.phase(RESIDENT).orElseThrow().phase());
    }

    @Test
    void tickSnapshotFeedsReservationAwareHintsIntoGrowthQueue() {
        BannerModSettlementOrchestrator.LevelRuntimeState state = BannerModSettlementOrchestrator.detachedStateForTests(JobHandlerRegistry.defaults());
        BannerModSettlementSnapshot base = settlementSnapshot(NIGHT_TICK, true);
        BannerModSettlementSnapshot hintedSnapshot = new BannerModSettlementSnapshot(
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
                BannerModSettlementStockpileSummary.empty(),
                base.marketState(),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementProjectCandidateSnapshot.empty(),
                new BannerModSettlementTradeRouteHandoffSnapshot(
                        1,
                        1,
                        0,
                        0,
                        2,
                        12,
                        List.of(new BannerModSettlementDesiredGoodSnapshot("market_goods", 0)),
                        List.of(),
                        List.of()
                ),
                new BannerModSettlementSupplySignalState(
                        1,
                        0,
                        0,
                        8,
                        List.of(new BannerModSettlementSupplySignal("market_goods", 0, 0, 0, 8))
                ),
                base.residents(),
                base.buildings()
        );

        BannerModSettlementOrchestrator.tickSnapshot(state, hintedSnapshot, null, NIGHT_TICK);

        List<PendingProject> queuedProjects = state.projectRuntime.snapshot(CLAIM);
        assertFalse(queuedProjects.isEmpty(), "reservation-aware hint snapshot should drive live project scoring");
        assertEquals(BannerModSettlementBuildingProfileSeed.MARKET, queuedProjects.get(0).profileSeed());
    }

    @Test
    void batchSnapshotOrderIsSortedOncePerMaintenanceCycle() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        BannerModSettlementManager manager = new BannerModSettlementManager();
        BannerModSettlementSnapshot base = settlementSnapshot(DAY_TICK, false);
        manager.putSnapshot(withClaim(base, third));
        manager.putSnapshot(withClaim(base, first));
        BannerModSettlementOrchestrator.LevelRuntimeState state = BannerModSettlementOrchestrator.detachedStateForTests(JobHandlerRegistry.defaults());

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
        BannerModSettlementOrchestrator.LevelRuntimeState state = BannerModSettlementOrchestrator.detachedStateForTests(registry);

        BannerModSettlementClaimTickService.tickSnapshot(state, settlementSnapshot(NIGHT_TICK, true), null, null, NIGHT_TICK);

        assertEquals(HOME, state.homeRuntime.homeFor(RESIDENT).orElseThrow().homeBuildingUuid());
        assertTrue(state.sellerRuntime.phase(RESIDENT).isPresent());
        assertFalse(state.projectRuntime.snapshot(CLAIM).isEmpty());
    }

    private static BannerModSettlementSnapshot settlementSnapshot(long gameTime, boolean includeSellerDispatch) {
        BannerModSettlementResidentServiceContract serviceContract = new BannerModSettlementResidentServiceContract(
                BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                MARKET,
                "market_area"
        );
        BannerModSettlementResidentRecord resident = new BannerModSettlementResidentRecord(
                RESIDENT,
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR,
                serviceContract,
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
                "teamA",
                MARKET,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );

        BannerModSettlementBuildingRecord home = new BannerModSettlementBuildingRecord(
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
        BannerModSettlementBuildingRecord market = new BannerModSettlementBuildingRecord(
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

        BannerModSettlementMarketState marketState = new BannerModSettlementMarketState(
                1,
                1,
                16,
                8,
                1,
                1,
                List.of(new BannerModSettlementMarketRecord(MARKET, "Market", true, 16, 8)),
                includeSellerDispatch
                        ? List.of(new BannerModSettlementSellerDispatchRecord(
                        RESIDENT,
                        MARKET,
                        "Market",
                        BannerModSettlementSellerDispatchState.READY
                ))
                        : List.of()
        );

        return new BannerModSettlementSnapshot(
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
                BannerModSettlementStockpileSummary.empty(),
                marketState,
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                List.of(resident),
                List.of(home, market)
        );
    }

    private static BannerModSettlementSnapshot withClaim(BannerModSettlementSnapshot base, UUID claimUuid) {
        return new BannerModSettlementSnapshot(
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
        public BannerModSettlementJobHandlerSeed handles() {
            return BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR;
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
