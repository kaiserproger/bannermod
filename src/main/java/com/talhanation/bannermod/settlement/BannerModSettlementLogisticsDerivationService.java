package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.shared.logistics.BannerModLogisticsReservation;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsRoute;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeEntrypoint;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionRecord;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeSummary;

import java.util.List;

final class BannerModSettlementLogisticsDerivationService {

    private BannerModSettlementLogisticsDerivationService() {
    }

    static LogisticsResult derive(List<BannerModSettlementBuildingRecord> buildings,
                                  List<BannerModSettlementResidentRecord> residents,
                                  BannerModSettlementMarketState marketState,
                                  List<BannerModSeaTradeEntrypoint> liveSeaTradeEntrypoints,
                                  List<BannerModLogisticsRoute> localRoutes,
                                  List<BannerModLogisticsReservation> reservations,
                                  List<BannerModSeaTradeExecutionRecord> localSeaTradeExecutions,
                                  boolean governedSettlement,
                                  boolean claimedSettlement) {
        BannerModSeaTradeSummary.Summary seaTradeSummary = BannerModSeaTradeSummary.summarise(liveSeaTradeEntrypoints);
        BannerModSettlementSnapshotRuntime.ReservationSignalSeed reservationSignalSeed = BannerModSettlementSnapshotRuntime.summarizeReservationSignalSeed(
                buildings,
                localRoutes,
                reservations
        );
        BannerModSettlementStockpileSummary stockpileSummary = BannerModSettlementSnapshotRuntime.summarizeStockpiles(buildings, liveSeaTradeEntrypoints);
        BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot = BannerModSettlementSnapshotRuntime.summarizeDesiredGoods(
                buildings,
                stockpileSummary,
                marketState,
                seaTradeSummary
        );
        BannerModSettlementProjectCandidateSnapshot projectCandidateSnapshot = BannerModSettlementSnapshotRuntime.summarizeProjectCandidate(
                buildings,
                stockpileSummary,
                desiredGoodsSnapshot,
                marketState,
                governedSettlement,
                claimedSettlement
        );
        BannerModSettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot = BannerModSettlementSnapshotRuntime.summarizeTradeRouteHandoffSnapshot(
                stockpileSummary,
                marketState,
                desiredGoodsSnapshot,
                reservationSignalSeed,
                seaTradeSummary,
                localSeaTradeExecutions
        );
        BannerModSettlementSupplySignalState supplySignalState = BannerModSettlementSnapshotRuntime.summarizeSupplySignals(
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

    record LogisticsResult(BannerModSettlementStockpileSummary stockpileSummary,
                           BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
                           BannerModSettlementProjectCandidateSnapshot projectCandidateSnapshot,
                           BannerModSettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
                           BannerModSettlementSupplySignalState supplySignalState,
                           BannerModSettlementSnapshotRuntime.ReservationSignalSeed reservationSignalSeed) {
    }
}
