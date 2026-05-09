package com.talhanation.bannermod.settlement.growth;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.SettlementBuildingCategory;
import com.talhanation.bannermod.settlement.SettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodSnapshot;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignal;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

class SettlementGrowthManagerTest {

    private static final SettlementMarketState NON_EMPTY_MARKET =
            new SettlementMarketState(1, 1, 0, 0, 0, 0, List.of(), List.of());

    @Test
    void emptySnapshotYieldsEmptyQueue() {
        List<PendingProject> queue = SettlementGrowthManager.evaluateGrowthQueue(emptyContext(), 8);
        assertTrue(queue.isEmpty(), "empty context should produce no candidates");
    }

    @Test
    void housingShortageYieldsNewBuildingInGeneralCategory() {
        // Residents exceed capacity and workers are unassigned → housing pressure.
        SettlementGrowthContext ctx = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementMarketState.empty(),
                2, 2, 3,
                100L
        );

        List<PendingProject> queue = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertFalse(queue.isEmpty(), "saturated settlement should have a housing candidate");
        PendingProject top = queue.get(0);
        assertEquals(ProjectKind.NEW_BUILDING, top.kind());
        // Housing currently falls under GENERAL since no dedicated category exists.
        assertEquals(SettlementBuildingCategory.GENERAL, top.buildingCategory());
        assertSame(SettlementBuildingProfileSeed.GENERAL, top.profileSeed());
    }

    @Test
    void desiredGoodShortagePrioritisesMatchingProducer() {
        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("food", 5)
        ));
        SettlementProjectCandidateSnapshot seed = new SettlementProjectCandidateSnapshot(
                "seed", SettlementBuildingProfileSeed.STORAGE, 0, false, false, List.of()
        );
        SettlementGrowthContext ctx = ctxOf(seed, desired, NON_EMPTY_MARKET, 0, 0, 0, 0L);

        List<PendingProject> queue = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertFalse(queue.isEmpty());
        PendingProject top = queue.get(0);
        assertSame(SettlementBuildingProfileSeed.FOOD_PRODUCTION, top.profileSeed());
        assertEquals(SettlementBuildingCategory.FOOD, top.buildingCategory());
    }

    @Test
    void reservationAwareHintsCanCreateDemandWithoutDesiredGoodsSnapshot() {
        SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot = new SettlementTradeRouteHandoffSnapshot(
                1, 1, 0, 0, 2, 12,
                List.of(new SettlementDesiredGoodSnapshot("market_goods", 0)),
                List.of(),
                List.of()
        );
        SettlementSupplySignalState supplySignalState = new SettlementSupplySignalState(
                1, 0, 0, 8,
                List.of(new SettlementSupplySignal("market_goods", 0, 0, 0, 8))
        );
        SettlementGrowthContext ctx = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                NON_EMPTY_MARKET,
                tradeRouteHandoffSnapshot,
                supplySignalState,
                0, 0, 0, 0L
        );

        List<PendingProject> queue = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertFalse(queue.isEmpty());
        assertSame(SettlementBuildingProfileSeed.MARKET, queue.get(0).profileSeed());
    }

    @Test
    void concreteSupplyShortageOutranksBroadDesiredDemand() {
        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("market_goods", 8)
        ));
        SettlementSupplySignalState supplySignalState = new SettlementSupplySignalState(
                1, 1, 2, 0,
                List.of(new SettlementSupplySignal("food", 1, 0, 2, 0))
        );
        SettlementGrowthContext ctx = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                SettlementTradeRouteHandoffSnapshot.empty(),
                supplySignalState,
                0, 0, 0, 77L
        );

        List<PendingProject> queue = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertFalse(queue.isEmpty());
        assertSame(SettlementBuildingProfileSeed.FOOD_PRODUCTION, queue.get(0).profileSeed());
    }

    @Test
    void sameGrowthProfileKeepsStableProjectIdAcrossTicks() {
        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("food", 2)
        ));
        SettlementGrowthContext early = ctxOf(
                SettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 10L);
        SettlementGrowthContext late = ctxOf(
                SettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 200L);

        PendingProject first = SettlementGrowthManager.pickNextProject(early).orElseThrow();
        PendingProject second = SettlementGrowthManager.pickNextProject(late).orElseThrow();

        assertEquals(first.projectId(), second.projectId());
        assertNotEquals(first.proposedAtGameTime(), second.proposedAtGameTime());
    }

    @Test
    void pickNextProjectMirrorsTopOfQueue() {
        assertEquals(Optional.empty(), SettlementGrowthManager.pickNextProject(emptyContext()));

        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("materials", 2)
        ));
        SettlementGrowthContext ctx = ctxOf(
                SettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 42L);

        List<PendingProject> queue = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);
        Optional<PendingProject> next = SettlementGrowthManager.pickNextProject(ctx);
        assertTrue(next.isPresent());
        assertEquals(queue.get(0), next.get());
    }

    @Test
    void maxQueueSizeZeroReturnsEmptyList() {
        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("food", 3),
                new SettlementDesiredGoodSnapshot("materials", 3)
        ));
        SettlementGrowthContext ctx = ctxOf(
                SettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 0L);

        assertTrue(SettlementGrowthManager.evaluateGrowthQueue(ctx, 0).isEmpty());
        assertTrue(SettlementGrowthManager.evaluateGrowthQueue(ctx, -1).isEmpty());
    }

    @Test
    void tieBreakIsDeterministicOnOrdinalThenHash() {
        // "food" and "materials" both have driverCount=1 => identical base score.
        // FOOD (ordinal 0) precedes MATERIAL (ordinal 1), so the food candidate wins.
        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("food", 1),
                new SettlementDesiredGoodSnapshot("materials", 1)
        ));
        SettlementGrowthContext ctx = ctxOf(
                SettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 7L);

        List<PendingProject> first = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);
        List<PendingProject> second = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);
        assertEquals(first, second, "deterministic ordering expected across invocations");
        assertTrue(first.size() >= 2);
        assertEquals(first.get(0).priorityScore(), first.get(1).priorityScore(),
                "first two candidates must be a genuine tie on score for this test");
        assertSame(SettlementBuildingProfileSeed.FOOD_PRODUCTION, first.get(0).profileSeed());
        assertSame(SettlementBuildingProfileSeed.MATERIAL_PRODUCTION, first.get(1).profileSeed());
        assertNotEquals(first.get(0), first.get(1));
    }

    @Test
    void governorPriorityCanCreateConstructionCandidateWithoutOtherDemand() {
        SettlementGrowthContext ctx = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementMarketState.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                0, 0, 0,
                governorSnapshot(2, 3, List.of()),
                15L
        );

        List<PendingProject> queue = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertEquals(1, queue.size());
        assertSame(SettlementBuildingProfileSeed.CONSTRUCTION, queue.get(0).profileSeed());
        assertEquals(ProjectBlocker.NONE, queue.get(0).blockerReason());
    }

    @Test
    void governorPriorityBoostsExistingConstructionDemandInsteadOfReplacingIt() {
        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("construction_materials", 1)
        ));
        SettlementGrowthContext baseline = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                0, 0, 0,
                null,
                40L
        );
        SettlementGrowthContext boosted = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                0, 0, 0,
                governorSnapshot(1, 2, List.of()),
                40L
        );

        PendingProject baselineProject = SettlementGrowthManager.pickNextProject(baseline).orElseThrow();
        PendingProject boostedProject = SettlementGrowthManager.pickNextProject(boosted).orElseThrow();

        assertSame(SettlementBuildingProfileSeed.CONSTRUCTION, baselineProject.profileSeed());
        assertSame(SettlementBuildingProfileSeed.CONSTRUCTION, boostedProject.profileSeed());
        assertTrue(boostedProject.priorityScore() > baselineProject.priorityScore());
    }

    @Test
    void siegeAddsDefensiveFallbackAndBlocksCivilianExpansion() {
        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("food", 2)
        ));
        SettlementGrowthContext ctx = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                0, 0, 0,
                governorSnapshot(0, 0, List.of("Under_Siege")),
                99L
        );

        List<PendingProject> queue = SettlementGrowthManager.evaluateGrowthQueue(ctx, 4);
        PendingProject foodProject = projectFor(queue, SettlementBuildingProfileSeed.FOOD_PRODUCTION).orElseThrow();

        assertFalse(queue.isEmpty());
        assertSame(SettlementBuildingProfileSeed.CONSTRUCTION, queue.get(0).profileSeed());
        assertEquals(ProjectBlocker.NONE, queue.get(0).blockerReason());
        assertEquals(ProjectBlocker.UNDER_SIEGE, foodProject.blockerReason());
    }

    @Test
    void tradeRouteDemandBonusAmplifiesStorageAndMarketScoring() {
        SettlementDesiredGoodsSnapshot desired = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("storage_type:merchants", 1),
                new SettlementDesiredGoodSnapshot("trade_stock", 1)
        ));
        SettlementTradeRouteHandoffSnapshot boostedHandoff = new SettlementTradeRouteHandoffSnapshot(
                1,
                1,
                2,
                2,
                3,
                12,
                List.of(),
                List.of(),
                List.of()
        );
        SettlementGrowthContext baseline = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                0, 0, 0,
                null,
                0L
        );
        SettlementGrowthContext boosted = ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                boostedHandoff,
                SettlementSupplySignalState.empty(),
                0, 0, 0,
                null,
                0L
        );

        List<PendingProject> baselineQueue = SettlementGrowthManager.evaluateGrowthQueue(baseline, 4);
        List<PendingProject> boostedQueue = SettlementGrowthManager.evaluateGrowthQueue(boosted, 4);

        assertTrue(projectFor(boostedQueue, SettlementBuildingProfileSeed.STORAGE).orElseThrow().priorityScore()
                > projectFor(baselineQueue, SettlementBuildingProfileSeed.STORAGE).orElseThrow().priorityScore());
        assertTrue(projectFor(boostedQueue, SettlementBuildingProfileSeed.MARKET).orElseThrow().priorityScore()
                > projectFor(baselineQueue, SettlementBuildingProfileSeed.MARKET).orElseThrow().priorityScore());
    }

    private static SettlementGrowthContext emptyContext() {
        return ctxOf(
                SettlementProjectCandidateSnapshot.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementMarketState.empty(),
                0, 0, 0, 0L
        );
    }

    private static SettlementGrowthContext ctxOf(
            SettlementProjectCandidateSnapshot seed,
            SettlementDesiredGoodsSnapshot desired,
            SettlementMarketState market,
            int residentCapacity,
            int assignedResidentCount,
            int unassignedWorkerCount,
            long gameTime
    ) {
        return new SettlementGrowthContext(
                seed, desired,
                SettlementStockpileSummary.empty(),
                market,
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(), List.of(),
                residentCapacity, assignedResidentCount, unassignedWorkerCount, 0,
                null, gameTime
        );
    }

    private static SettlementGrowthContext ctxOf(
            SettlementProjectCandidateSnapshot seed,
            SettlementDesiredGoodsSnapshot desired,
            SettlementMarketState market,
            SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
            SettlementSupplySignalState supplySignalState,
            int residentCapacity,
            int assignedResidentCount,
            int unassignedWorkerCount,
            long gameTime
    ) {
        return ctxOf(
                seed,
                desired,
                market,
                tradeRouteHandoffSnapshot,
                supplySignalState,
                residentCapacity,
                assignedResidentCount,
                unassignedWorkerCount,
                null,
                gameTime
        );
    }

    private static SettlementGrowthContext ctxOf(
            SettlementProjectCandidateSnapshot seed,
            SettlementDesiredGoodsSnapshot desired,
            SettlementMarketState market,
            SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
            SettlementSupplySignalState supplySignalState,
            int residentCapacity,
            int assignedResidentCount,
            int unassignedWorkerCount,
            BannerModGovernorSnapshot governorSnapshot,
            long gameTime
    ) {
        return new SettlementGrowthContext(
                seed,
                desired,
                SettlementStockpileSummary.empty(),
                market,
                tradeRouteHandoffSnapshot,
                supplySignalState,
                List.of(),
                List.of(),
                residentCapacity,
                assignedResidentCount,
                unassignedWorkerCount,
                0,
                governorSnapshot,
                gameTime
        );
    }

    private static BannerModGovernorSnapshot governorSnapshot(int garrisonPriority,
                                                              int fortificationPriority,
                                                              List<String> incidentTokens) {
        return new BannerModGovernorSnapshot(
                UUID.randomUUID(),
                0,
                0,
                null,
                null,
                null,
                0L,
                0L,
                0,
                0,
                0,
                0,
                0,
                0,
                garrisonPriority,
                fortificationPriority,
                0,
                incidentTokens,
                List.of()
        );
    }

    private static Optional<PendingProject> projectFor(List<PendingProject> queue,
                                                       SettlementBuildingProfileSeed profileSeed) {
        return queue.stream().filter(project -> project.profileSeed() == profileSeed).findFirst();
    }
}
