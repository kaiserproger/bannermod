package com.talhanation.bannermod.settlement.growth;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketState;
import com.talhanation.bannermod.settlement.BannerModSettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementStockpileSummary;
import com.talhanation.bannermod.settlement.BannerModSettlementSupplySignalState;
import com.talhanation.bannermod.settlement.BannerModSettlementTradeRouteHandoffSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModSettlementGrowthContextTest {

    @Test
    void constructorNormalizesNullSeedsAndNegativeCounts() {
        BannerModSettlementGrowthContext ctx = new BannerModSettlementGrowthContext(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                -2,
                -3,
                -4,
                -5,
                null,
                7L
        );

        assertEquals(BannerModSettlementProjectCandidateSnapshot.empty(), ctx.projectCandidateSnapshot());
        assertEquals(BannerModSettlementDesiredGoodsSnapshot.empty(), ctx.desiredGoodsSnapshot());
        assertEquals(BannerModSettlementStockpileSummary.empty(), ctx.stockpileSummary());
        assertEquals(BannerModSettlementMarketState.empty(), ctx.marketState());
        assertEquals(BannerModSettlementTradeRouteHandoffSnapshot.empty(), ctx.tradeRouteHandoffSnapshot());
        assertEquals(BannerModSettlementSupplySignalState.empty(), ctx.supplySignalState());
        assertTrue(ctx.buildings().isEmpty());
        assertTrue(ctx.residents().isEmpty());
        assertEquals(0, ctx.residentCapacity());
        assertEquals(0, ctx.assignedResidentCount());
        assertEquals(0, ctx.unassignedWorkerCount());
        assertEquals(0, ctx.missingWorkAreaAssignmentCount());
        assertEquals(0, ctx.housingHeadroom());
        assertFalse(ctx.isUnderSiege());
    }

    @Test
    void fromSnapshotCopiesSnapshotFieldsAndCalculatesHeadroom() {
        BannerModSettlementSnapshot snapshot = snapshot(3, 1, 2, 1);

        BannerModSettlementGrowthContext ctx = BannerModSettlementGrowthContext.fromSnapshot(snapshot, 55L);

        assertEquals(snapshot.projectCandidateSnapshot(), ctx.projectCandidateSnapshot());
        assertEquals(snapshot.desiredGoodsSnapshot(), ctx.desiredGoodsSnapshot());
        assertEquals(snapshot.stockpileSummary(), ctx.stockpileSummary());
        assertEquals(snapshot.marketState(), ctx.marketState());
        assertEquals(snapshot.tradeRouteHandoffSnapshot(), ctx.tradeRouteHandoffSnapshot());
        assertEquals(snapshot.supplySignalState(), ctx.supplySignalState());
        assertEquals(2, ctx.housingHeadroom());
        assertEquals(55L, ctx.gameTime());
        assertEquals(null, ctx.governorSnapshot());
    }

    @Test
    void fromSnapshotDetectsUnderSiegeCaseInsensitivelyAndRejectsNullSnapshot() {
        BannerModGovernorSnapshot governorSnapshot = new BannerModGovernorSnapshot(
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
                0,
                0,
                0,
                List.of("Under_Siege", "tax_warning"),
                List.of()
        );

        BannerModSettlementGrowthContext ctx = BannerModSettlementGrowthContext.fromSnapshot(snapshot(0, 0, 0, 0), governorSnapshot, 10L);

        assertTrue(ctx.isUnderSiege());
        assertEquals(governorSnapshot, ctx.governorSnapshot());
        assertThrows(IllegalArgumentException.class, () -> BannerModSettlementGrowthContext.fromSnapshot(null, 10L));
    }

    private static BannerModSettlementSnapshot snapshot(int residentCapacity,
                                                        int assignedResidentCount,
                                                        int unassignedWorkerCount,
                                                        int missingWorkAreaAssignmentCount) {
        return new BannerModSettlementSnapshot(
                UUID.randomUUID(),
                0,
                0,
                "blueguild",
                10L,
                residentCapacity,
                assignedResidentCount,
                unassignedWorkerCount,
                missingWorkAreaAssignmentCount,
                0,
                0,
                BannerModSettlementStockpileSummary.empty(),
                BannerModSettlementMarketState.empty(),
                new BannerModSettlementDesiredGoodsSnapshot(List.of(new BannerModSettlementDesiredGoodSnapshot("food", 2))),
                new BannerModSettlementProjectCandidateSnapshot(
                        "seed",
                        com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed.GENERAL,
                        2,
                        true,
                        true,
                        List.of("housing_pressure")
                ),
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                List.of(),
                List.of()
        );
    }
}
