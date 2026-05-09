package com.talhanation.bannermod.settlement.growth;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodSnapshot;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementGrowthContextTest {

    @Test
    void constructorNormalizesNullSeedsAndNegativeCounts() {
        SettlementGrowthContext ctx = new SettlementGrowthContext(
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

        assertEquals(SettlementProjectCandidateSnapshot.empty(), ctx.projectCandidateSnapshot());
        assertEquals(SettlementDesiredGoodsSnapshot.empty(), ctx.desiredGoodsSnapshot());
        assertEquals(SettlementStockpileSummary.empty(), ctx.stockpileSummary());
        assertEquals(SettlementMarketState.empty(), ctx.marketState());
        assertEquals(SettlementTradeRouteHandoffSnapshot.empty(), ctx.tradeRouteHandoffSnapshot());
        assertEquals(SettlementSupplySignalState.empty(), ctx.supplySignalState());
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
        SettlementSnapshot snapshot = snapshot(3, 1, 2, 1);

        SettlementGrowthContext ctx = SettlementGrowthContext.fromSnapshot(snapshot, 55L);

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

        SettlementGrowthContext ctx = SettlementGrowthContext.fromSnapshot(snapshot(0, 0, 0, 0), governorSnapshot, 10L);

        assertTrue(ctx.isUnderSiege());
        assertEquals(governorSnapshot, ctx.governorSnapshot());
        assertThrows(IllegalArgumentException.class, () -> SettlementGrowthContext.fromSnapshot(null, 10L));
    }

    private static SettlementSnapshot snapshot(int residentCapacity,
                                                        int assignedResidentCount,
                                                        int unassignedWorkerCount,
                                                        int missingWorkAreaAssignmentCount) {
        return new SettlementSnapshot(
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
                SettlementStockpileSummary.empty(),
                SettlementMarketState.empty(),
                new SettlementDesiredGoodsSnapshot(List.of(new SettlementDesiredGoodSnapshot("food", 2))),
                new SettlementProjectCandidateSnapshot(
                        "seed",
                        com.talhanation.bannermod.settlement.SettlementBuildingProfileSeed.GENERAL,
                        2,
                        true,
                        true,
                        List.of("housing_pressure")
                ),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(),
                List.of()
        );
    }
}
