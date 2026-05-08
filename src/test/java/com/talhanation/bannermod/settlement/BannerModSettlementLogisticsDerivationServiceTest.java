package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeSummary;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementLogisticsDerivationServiceTest {

    @Test
    void logisticsDerivationServiceCombinesStockpileProjectAndSupplySeeds() {
        UUID storageUuid = UUID.randomUUID();
        UUID marketUuid = UUID.randomUUID();
        SettlementBuildingRecord storage = new SettlementBuildingRecord(storageUuid, "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 2, 54, true, true, List.of("merchants"));
        SettlementBuildingRecord market = new SettlementBuildingRecord(marketUuid, "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of());
        SettlementResidentRecord seller = new SettlementResidentRecord(
                UUID.randomUUID(),
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                SettlementResidentServiceContract.defaultFor(
                        SettlementResidentRole.CONTROLLED_WORKER,
                        SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING,
                        marketUuid,
                        "bannermod:market_area"
                ),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                marketUuid,
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
        SettlementMarketState marketState = new SettlementMarketState(
                1,
                1,
                27,
                9,
                1,
                1,
                List.of(new SettlementMarketRecord(marketUuid, "Harbor Square", true, 27, 9)),
                List.of(new SettlementSellerDispatchRecord(seller.residentUuid(), marketUuid, "Harbor Square", SettlementSellerDispatchState.READY))
        );

        SettlementLogisticsDerivationService.LogisticsResult logistics = SettlementLogisticsDerivationService.derive(
                List.of(storage, market),
                List.of(seller),
                marketState,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                true
        );

        SettlementStockpileSummary expectedStockpile = SettlementSnapshotRuntime.summarizeStockpiles(List.of(storage, market), List.of());
        SettlementDesiredGoodsSnapshot expectedDesiredGoods = SettlementSnapshotRuntime.summarizeDesiredGoods(
                List.of(storage, market),
                expectedStockpile,
                marketState,
                BannerModSeaTradeSummary.summarise(List.of())
        );
        SettlementProjectCandidateSnapshot expectedProject = SettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(storage, market),
                expectedStockpile,
                expectedDesiredGoods,
                marketState,
                true,
                true
        );
        SettlementTradeRouteHandoffSnapshot expectedTradeRouteHandoff = SettlementSnapshotRuntime.summarizeTradeRouteHandoffSnapshot(
                expectedStockpile,
                marketState,
                expectedDesiredGoods,
                SettlementSnapshotRuntime.ReservationSignalSeed.empty(),
                BannerModSeaTradeSummary.summarise(List.of()),
                List.of()
        );
        SettlementSupplySignalState expectedSupplySignals = SettlementSnapshotRuntime.summarizeSupplySignals(
                expectedDesiredGoods,
                expectedStockpile,
                marketState,
                List.of(seller),
                List.of(storage, market),
                SettlementSnapshotRuntime.ReservationSignalSeed.empty(),
                BannerModSeaTradeSummary.summarise(List.of())
        );

        assertEquals(expectedStockpile, logistics.stockpileSummary());
        assertEquals(expectedDesiredGoods, logistics.desiredGoodsSnapshot());
        assertEquals(expectedProject, logistics.projectCandidateSnapshot());
        assertEquals(expectedTradeRouteHandoff, logistics.tradeRouteHandoffSnapshot());
        assertEquals(expectedSupplySignals, logistics.supplySignalState());
    }
}
