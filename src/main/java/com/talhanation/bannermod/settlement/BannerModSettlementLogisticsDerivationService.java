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
        BannerModSettlementService.ReservationSignalSeed reservationSignalSeed = BannerModSettlementService.summarizeReservationSignalSeed(
                buildings,
                localRoutes,
                reservations
        );
        BannerModSettlementStockpileSummary stockpileSummary = BannerModSettlementService.summarizeStockpiles(buildings, liveSeaTradeEntrypoints);
        BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot = BannerModSettlementService.summarizeDesiredGoods(
                buildings,
                stockpileSummary,
                marketState,
                seaTradeSummary
        );
        BannerModSettlementProjectCandidateSnapshot projectCandidateSnapshot = BannerModSettlementService.summarizeProjectCandidate(
                buildings,
                stockpileSummary,
                desiredGoodsSnapshot,
                marketState,
                governedSettlement,
                claimedSettlement
        );
        BannerModSettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot = BannerModSettlementService.summarizeTradeRouteHandoffSnapshot(
                stockpileSummary,
                marketState,
                desiredGoodsSnapshot,
                reservationSignalSeed,
                seaTradeSummary,
                localSeaTradeExecutions
        );
        BannerModSettlementSupplySignalState supplySignalState = BannerModSettlementService.summarizeSupplySignals(
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
                           BannerModSettlementService.ReservationSignalSeed reservationSignalSeed) {
    }
}
