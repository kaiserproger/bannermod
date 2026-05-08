package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.shared.logistics.BannerModLogisticsReservation;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsRoute;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeEntrypoint;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionRecord;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeSummary;

import java.util.List;

final class SettlementLogisticsDerivationService {

    private SettlementLogisticsDerivationService() {
    }

    static LogisticsResult derive(List<SettlementBuildingRecord> buildings,
                                  List<SettlementResidentRecord> residents,
                                  SettlementMarketState marketState,
                                  List<BannerModSeaTradeEntrypoint> liveSeaTradeEntrypoints,
                                  List<BannerModLogisticsRoute> localRoutes,
                                  List<BannerModLogisticsReservation> reservations,
                                  List<BannerModSeaTradeExecutionRecord> localSeaTradeExecutions,
                                  boolean governedSettlement,
                                  boolean claimedSettlement) {
        BannerModSeaTradeSummary.Summary seaTradeSummary = BannerModSeaTradeSummary.summarise(liveSeaTradeEntrypoints);
        SettlementSnapshotRuntime.ReservationSignalSeed reservationSignalSeed = SettlementSnapshotRuntime.summarizeReservationSignalSeed(
                buildings,
                localRoutes,
                reservations
        );
        SettlementStockpileSummary stockpileSummary = SettlementSnapshotRuntime.summarizeStockpiles(buildings, liveSeaTradeEntrypoints);
        SettlementDesiredGoodsSnapshot desiredGoodsSnapshot = SettlementSnapshotRuntime.summarizeDesiredGoods(
                buildings,
                stockpileSummary,
                marketState,
                seaTradeSummary
        );
        SettlementProjectCandidateSnapshot projectCandidateSnapshot = SettlementSnapshotRuntime.summarizeProjectCandidate(
                buildings,
                stockpileSummary,
                desiredGoodsSnapshot,
                marketState,
                governedSettlement,
                claimedSettlement
        );
        SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot = SettlementSnapshotRuntime.summarizeTradeRouteHandoffSnapshot(
                stockpileSummary,
                marketState,
                desiredGoodsSnapshot,
                reservationSignalSeed,
                seaTradeSummary,
                localSeaTradeExecutions
        );
        SettlementSupplySignalState supplySignalState = SettlementSnapshotRuntime.summarizeSupplySignals(
                desiredGoodsSnapshot,
                stockpileSummary,
                marketState,
                residents,
                buildings,
                reservationSignalSeed,
                seaTradeSummary
        );
        return new LogisticsResult(
                stockpileSummary,
                desiredGoodsSnapshot,
                projectCandidateSnapshot,
                tradeRouteHandoffSnapshot,
                supplySignalState,
                reservationSignalSeed
        );
    }

    record LogisticsResult(SettlementStockpileSummary stockpileSummary,
                           SettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
                           SettlementProjectCandidateSnapshot projectCandidateSnapshot,
                           SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
                           SettlementSupplySignalState supplySignalState,
                           SettlementSnapshotRuntime.ReservationSignalSeed reservationSignalSeed) {
    }
}
