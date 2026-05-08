package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.settlement.bootstrap.SettlementRecord;
import com.talhanation.bannermod.settlement.bootstrap.SettlementStatus;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModSettlementLogisticsDerivationServiceTest {

    @Test
    void logisticsDerivationServiceCombinesStockpileProjectAndSupplySeeds() {
        UUID storageUuid = UUID.randomUUID();
        UUID marketUuid = UUID.randomUUID();
        BannerModSettlementBuildingRecord storage = new BannerModSettlementBuildingRecord(storageUuid, "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 2, 54, true, true, List.of("merchants"));
        BannerModSettlementBuildingRecord market = new BannerModSettlementBuildingRecord(marketUuid, "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of());
        BannerModSettlementResidentRecord seller = new BannerModSettlementResidentRecord(
                UUID.randomUUID(),
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY,
                BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR,
                BannerModSettlementResidentServiceContract.defaultFor(
                        BannerModSettlementResidentRole.CONTROLLED_WORKER,
                        BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING,
                        marketUuid,
                        "bannermod:market_area"
                ),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                marketUuid,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
        BannerModSettlementMarketState marketState = new BannerModSettlementMarketState(
                1,
                1,
                27,
                9,
                1,
                1,
                List.of(new BannerModSettlementMarketRecord(marketUuid, "Harbor Square", true, 27, 9)),
                List.of(new BannerModSettlementSellerDispatchRecord(seller.residentUuid(), marketUuid, "Harbor Square", BannerModSettlementSellerDispatchState.READY))
        );

        BannerModSettlementLogisticsDerivationService.LogisticsResult logistics = BannerModSettlementLogisticsDerivationService.derive(
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

        BannerModSettlementStockpileSummary expectedStockpile = BannerModSettlementSnapshotRuntime.summarizeStockpiles(List.of(storage, market), List.of());
        BannerModSettlementDesiredGoodsSnapshot expectedDesiredGoods = BannerModSettlementSnapshotRuntime.summarizeDesiredGoods(
                List.of(storage, market),
                expectedStockpile,
                marketState,
                BannerModSeaTradeSummary.summarise(List.of())
        );
        BannerModSettlementProjectCandidateSnapshot expectedProject = BannerModSettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(storage, market),
                expectedStockpile,
                expectedDesiredGoods,
                marketState,
                true,
                true
        );
        BannerModSettlementTradeRouteHandoffSnapshot expectedTradeRouteHandoff = BannerModSettlementSnapshotRuntime.summarizeTradeRouteHandoffSnapshot(
                expectedStockpile,
                marketState,
                expectedDesiredGoods,
                BannerModSettlementSnapshotRuntime.ReservationSignalSeed.empty(),
                BannerModSeaTradeSummary.summarise(List.of()),
                List.of()
        );
        BannerModSettlementSupplySignalState expectedSupplySignals = BannerModSettlementSnapshotRuntime.summarizeSupplySignals(
                expectedDesiredGoods,
                expectedStockpile,
                marketState,
                List.of(seller),
                List.of(storage, market),
                BannerModSettlementSnapshotRuntime.ReservationSignalSeed.empty(),
                BannerModSeaTradeSummary.summarise(List.of())
        );

        assertEquals(expectedStockpile, logistics.stockpileSummary());
        assertEquals(expectedDesiredGoods, logistics.desiredGoodsSnapshot());
        assertEquals(expectedProject, logistics.projectCandidateSnapshot());
        assertEquals(expectedTradeRouteHandoff, logistics.tradeRouteHandoffSnapshot());
        assertEquals(expectedSupplySignals, logistics.supplySignalState());
    }
}
