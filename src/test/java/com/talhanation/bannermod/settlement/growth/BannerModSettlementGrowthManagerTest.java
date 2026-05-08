package com.talhanation.bannermod.settlement.growth;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingCategory;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketState;
import com.talhanation.bannermod.settlement.BannerModSettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementStockpileSummary;
import com.talhanation.bannermod.settlement.BannerModSettlementSupplySignal;
import com.talhanation.bannermod.settlement.BannerModSettlementSupplySignalState;
import com.talhanation.bannermod.settlement.BannerModSettlementTradeRouteHandoffSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

class BannerModSettlementGrowthManagerTest {

    private static final BannerModSettlementMarketState NON_EMPTY_MARKET =
            new BannerModSettlementMarketState(1, 1, 0, 0, 0, 0, List.of(), List.of());

    @Test
    void emptySnapshotYieldsEmptyQueue() {
        List<PendingProject> queue = BannerModSettlementGrowthManager.evaluateGrowthQueue(emptyContext(), 8);
        assertTrue(queue.isEmpty(), "empty context should produce no candidates");
    }

    @Test
    void housingShortageYieldsNewBuildingInGeneralCategory() {
        // Residents exceed capacity and workers are unassigned → housing pressure.
        BannerModSettlementGrowthContext ctx = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementMarketState.empty(),
                2, 2, 3,
                100L
        );

        List<PendingProject> queue = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertFalse(queue.isEmpty(), "saturated settlement should have a housing candidate");
        PendingProject top = queue.get(0);
        assertEquals(ProjectKind.NEW_BUILDING, top.kind());
        // Housing currently falls under GENERAL since no dedicated category exists.
        assertEquals(BannerModSettlementBuildingCategory.GENERAL, top.buildingCategory());
        assertSame(BannerModSettlementBuildingProfileSeed.GENERAL, top.profileSeed());
    }

    @Test
    void desiredGoodShortagePrioritisesMatchingProducer() {
        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("food", 5)
        ));
        BannerModSettlementProjectCandidateSnapshot seed = new BannerModSettlementProjectCandidateSnapshot(
                "seed", BannerModSettlementBuildingProfileSeed.STORAGE, 0, false, false, List.of()
        );
        BannerModSettlementGrowthContext ctx = ctxOf(seed, desired, NON_EMPTY_MARKET, 0, 0, 0, 0L);

        List<PendingProject> queue = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertFalse(queue.isEmpty());
        PendingProject top = queue.get(0);
        assertSame(BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, top.profileSeed());
        assertEquals(BannerModSettlementBuildingCategory.FOOD, top.buildingCategory());
    }

    @Test
    void reservationAwareHintsCanCreateDemandWithoutDesiredGoodsSnapshot() {
        BannerModSettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot = new BannerModSettlementTradeRouteHandoffSnapshot(
                1, 1, 0, 0, 2, 12,
                List.of(new BannerModSettlementDesiredGoodSnapshot("market_goods", 0)),
                List.of(),
                List.of()
        );
        BannerModSettlementSupplySignalState supplySignalState = new BannerModSettlementSupplySignalState(
                1, 0, 0, 8,
                List.of(new BannerModSettlementSupplySignal("market_goods", 0, 0, 0, 8))
        );
        BannerModSettlementGrowthContext ctx = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                NON_EMPTY_MARKET,
                tradeRouteHandoffSnapshot,
                supplySignalState,
                0, 0, 0, 0L
        );

        List<PendingProject> queue = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertFalse(queue.isEmpty());
        assertSame(BannerModSettlementBuildingProfileSeed.MARKET, queue.get(0).profileSeed());
    }

    @Test
    void concreteSupplyShortageOutranksBroadDesiredDemand() {
        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("market_goods", 8)
        ));
        BannerModSettlementSupplySignalState supplySignalState = new BannerModSettlementSupplySignalState(
                1, 1, 2, 0,
                List.of(new BannerModSettlementSupplySignal("food", 1, 0, 2, 0))
        );
        BannerModSettlementGrowthContext ctx = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                supplySignalState,
                0, 0, 0, 77L
        );

        List<PendingProject> queue = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertFalse(queue.isEmpty());
        assertSame(BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, queue.get(0).profileSeed());
    }

    @Test
    void sameGrowthProfileKeepsStableProjectIdAcrossTicks() {
        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("food", 2)
        ));
        BannerModSettlementGrowthContext early = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 10L);
        BannerModSettlementGrowthContext late = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 200L);

        PendingProject first = BannerModSettlementGrowthManager.pickNextProject(early).orElseThrow();
        PendingProject second = BannerModSettlementGrowthManager.pickNextProject(late).orElseThrow();

        assertEquals(first.projectId(), second.projectId());
        assertNotEquals(first.proposedAtGameTime(), second.proposedAtGameTime());
    }

    @Test
    void pickNextProjectMirrorsTopOfQueue() {
        assertEquals(Optional.empty(), BannerModSettlementGrowthManager.pickNextProject(emptyContext()));

        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("materials", 2)
        ));
        BannerModSettlementGrowthContext ctx = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 42L);

        List<PendingProject> queue = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);
        Optional<PendingProject> next = BannerModSettlementGrowthManager.pickNextProject(ctx);
        assertTrue(next.isPresent());
        assertEquals(queue.get(0), next.get());
    }

    @Test
    void maxQueueSizeZeroReturnsEmptyList() {
        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("food", 3),
                new BannerModSettlementDesiredGoodSnapshot("materials", 3)
        ));
        BannerModSettlementGrowthContext ctx = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 0L);

        assertTrue(BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 0).isEmpty());
        assertTrue(BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, -1).isEmpty());
    }

    @Test
    void tieBreakIsDeterministicOnOrdinalThenHash() {
        // "food" and "materials" both have driverCount=1 => identical base score.
        // FOOD (ordinal 0) precedes MATERIAL (ordinal 1), so the food candidate wins.
        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("food", 1),
                new BannerModSettlementDesiredGoodSnapshot("materials", 1)
        ));
        BannerModSettlementGrowthContext ctx = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(), desired, NON_EMPTY_MARKET, 0, 0, 0, 7L);

        List<PendingProject> first = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);
        List<PendingProject> second = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);
        assertEquals(first, second, "deterministic ordering expected across invocations");
        assertTrue(first.size() >= 2);
        assertEquals(first.get(0).priorityScore(), first.get(1).priorityScore(),
                "first two candidates must be a genuine tie on score for this test");
        assertSame(BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, first.get(0).profileSeed());
        assertSame(BannerModSettlementBuildingProfileSeed.MATERIAL_PRODUCTION, first.get(1).profileSeed());
        assertNotEquals(first.get(0), first.get(1));
    }

    @Test
    void governorPriorityCanCreateConstructionCandidateWithoutOtherDemand() {
        BannerModSettlementGrowthContext ctx = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementMarketState.empty(),
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                0, 0, 0,
                governorSnapshot(2, 3, List.of()),
                15L
        );

        List<PendingProject> queue = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);

        assertEquals(1, queue.size());
        assertSame(BannerModSettlementBuildingProfileSeed.CONSTRUCTION, queue.get(0).profileSeed());
        assertEquals(ProjectBlocker.NONE, queue.get(0).blockerReason());
    }

    @Test
    void governorPriorityBoostsExistingConstructionDemandInsteadOfReplacingIt() {
        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("construction_materials", 1)
        ));
        BannerModSettlementGrowthContext baseline = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                0, 0, 0,
                null,
                40L
        );
        BannerModSettlementGrowthContext boosted = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                0, 0, 0,
                governorSnapshot(1, 2, List.of()),
                40L
        );

        PendingProject baselineProject = BannerModSettlementGrowthManager.pickNextProject(baseline).orElseThrow();
        PendingProject boostedProject = BannerModSettlementGrowthManager.pickNextProject(boosted).orElseThrow();

        assertSame(BannerModSettlementBuildingProfileSeed.CONSTRUCTION, baselineProject.profileSeed());
        assertSame(BannerModSettlementBuildingProfileSeed.CONSTRUCTION, boostedProject.profileSeed());
        assertTrue(boostedProject.priorityScore() > baselineProject.priorityScore());
    }

    @Test
    void siegeAddsDefensiveFallbackAndBlocksCivilianExpansion() {
        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("food", 2)
        ));
        BannerModSettlementGrowthContext ctx = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                0, 0, 0,
                governorSnapshot(0, 0, List.of("Under_Siege")),
                99L
        );

        List<PendingProject> queue = BannerModSettlementGrowthManager.evaluateGrowthQueue(ctx, 4);
        PendingProject foodProject = projectFor(queue, BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION).orElseThrow();

        assertFalse(queue.isEmpty());
        assertSame(BannerModSettlementBuildingProfileSeed.CONSTRUCTION, queue.get(0).profileSeed());
        assertEquals(ProjectBlocker.NONE, queue.get(0).blockerReason());
        assertEquals(ProjectBlocker.UNDER_SIEGE, foodProject.blockerReason());
    }

    @Test
    void tradeRouteDemandBonusAmplifiesStorageAndMarketScoring() {
        BannerModSettlementDesiredGoodsSnapshot desired = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("storage_type:merchants", 1),
                new BannerModSettlementDesiredGoodSnapshot("trade_stock", 1)
        ));
        BannerModSettlementTradeRouteHandoffSnapshot boostedHandoff = new BannerModSettlementTradeRouteHandoffSnapshot(
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
        BannerModSettlementGrowthContext baseline = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                0, 0, 0,
                null,
                0L
        );
        BannerModSettlementGrowthContext boosted = ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                desired,
                NON_EMPTY_MARKET,
                boostedHandoff,
                BannerModSettlementSupplySignalState.empty(),
                0, 0, 0,
                null,
                0L
        );

        List<PendingProject> baselineQueue = BannerModSettlementGrowthManager.evaluateGrowthQueue(baseline, 4);
        List<PendingProject> boostedQueue = BannerModSettlementGrowthManager.evaluateGrowthQueue(boosted, 4);

        assertTrue(projectFor(boostedQueue, BannerModSettlementBuildingProfileSeed.STORAGE).orElseThrow().priorityScore()
                > projectFor(baselineQueue, BannerModSettlementBuildingProfileSeed.STORAGE).orElseThrow().priorityScore());
        assertTrue(projectFor(boostedQueue, BannerModSettlementBuildingProfileSeed.MARKET).orElseThrow().priorityScore()
                > projectFor(baselineQueue, BannerModSettlementBuildingProfileSeed.MARKET).orElseThrow().priorityScore());
    }

    private static BannerModSettlementGrowthContext emptyContext() {
        return ctxOf(
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementMarketState.empty(),
                0, 0, 0, 0L
        );
    }

    private static BannerModSettlementGrowthContext ctxOf(
            BannerModSettlementProjectCandidateSnapshot seed,
            BannerModSettlementDesiredGoodsSnapshot desired,
            BannerModSettlementMarketState market,
            int residentCapacity,
            int assignedResidentCount,
            int unassignedWorkerCount,
            long gameTime
    ) {
        return new BannerModSettlementGrowthContext(
                seed, desired,
                BannerModSettlementStockpileSummary.empty(),
                market,
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                List.of(), List.of(),
                residentCapacity, assignedResidentCount, unassignedWorkerCount, 0,
                null, gameTime
        );
    }

    private static BannerModSettlementGrowthContext ctxOf(
            BannerModSettlementProjectCandidateSnapshot seed,
            BannerModSettlementDesiredGoodsSnapshot desired,
            BannerModSettlementMarketState market,
            BannerModSettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
            BannerModSettlementSupplySignalState supplySignalState,
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

    private static BannerModSettlementGrowthContext ctxOf(
            BannerModSettlementProjectCandidateSnapshot seed,
            BannerModSettlementDesiredGoodsSnapshot desired,
            BannerModSettlementMarketState market,
            BannerModSettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
            BannerModSettlementSupplySignalState supplySignalState,
            int residentCapacity,
            int assignedResidentCount,
            int unassignedWorkerCount,
            BannerModGovernorSnapshot governorSnapshot,
            long gameTime
    ) {
        return new BannerModSettlementGrowthContext(
                seed,
                desired,
                BannerModSettlementStockpileSummary.empty(),
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
                                                       BannerModSettlementBuildingProfileSeed profileSeed) {
        return queue.stream().filter(project -> project.profileSeed() == profileSeed).findFirst();
    }
}
