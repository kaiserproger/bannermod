package com.talhanation.bannermod.settlement.growth;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementResidentRecord;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Immutable input bundle for {@link SettlementGrowthManager}. Holds
 * just the snapshots and signals needed to score growth candidates. Use
 * {@link #fromSnapshot} for the common case; the canonical record constructor
 * is left accessible for tests that want a minimal input.
 */
public record SettlementGrowthContext(
        SettlementProjectCandidateSnapshot projectCandidateSnapshot,
        SettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
        SettlementStockpileSummary stockpileSummary,
        SettlementMarketState marketState,
        SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot,
        SettlementSupplySignalState supplySignalState,
        List<SettlementBuildingRecord> buildings,
        List<SettlementResidentRecord> residents,
        int residentCapacity,
        int assignedResidentCount,
        int unassignedWorkerCount,
        int missingWorkAreaAssignmentCount,
        @Nullable BannerModGovernorSnapshot governorSnapshot,
        long gameTime
) {
    public SettlementGrowthContext {
        projectCandidateSnapshot = projectCandidateSnapshot == null
                ? SettlementProjectCandidateSnapshot.empty() : projectCandidateSnapshot;
        desiredGoodsSnapshot = desiredGoodsSnapshot == null
                ? SettlementDesiredGoodsSnapshot.empty() : desiredGoodsSnapshot;
        stockpileSummary = stockpileSummary == null
                ? SettlementStockpileSummary.empty() : stockpileSummary;
        marketState = marketState == null
                ? SettlementMarketState.empty() : marketState;
        tradeRouteHandoffSnapshot = tradeRouteHandoffSnapshot == null
                ? SettlementTradeRouteHandoffSnapshot.empty() : tradeRouteHandoffSnapshot;
        supplySignalState = supplySignalState == null
                ? SettlementSupplySignalState.empty() : supplySignalState;
        buildings = List.copyOf(buildings == null ? List.of() : buildings);
        residents = List.copyOf(residents == null ? List.of() : residents);
        residentCapacity = Math.max(0, residentCapacity);
        assignedResidentCount = Math.max(0, assignedResidentCount);
        unassignedWorkerCount = Math.max(0, unassignedWorkerCount);
        missingWorkAreaAssignmentCount = Math.max(0, missingWorkAreaAssignmentCount);
    }

    public static SettlementGrowthContext fromSnapshot(
            SettlementSnapshot snapshot,
            long gameTime
    ) {
        return fromSnapshot(snapshot, null, gameTime);
    }

    public static SettlementGrowthContext fromSnapshot(
            SettlementSnapshot snapshot,
            @Nullable BannerModGovernorSnapshot governorSnapshot,
            long gameTime
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return new SettlementGrowthContext(
                snapshot.projectCandidateSnapshot(),
                snapshot.desiredGoodsSnapshot(),
                snapshot.stockpileSummary(),
                snapshot.marketState(),
                snapshot.tradeRouteHandoffSnapshot(),
                snapshot.supplySignalState(),
                snapshot.buildings(),
                snapshot.residents(),
                snapshot.residentCapacity(),
                snapshot.assignedResidentCount(),
                snapshot.unassignedWorkerCount(),
                snapshot.missingWorkAreaAssignmentCount(),
                governorSnapshot,
                gameTime
        );
    }

    /** True when the governor rollup carries the {@code under_siege} incident. */
    public boolean isUnderSiege() {
        if (this.governorSnapshot == null) {
            return false;
        }
        for (String token : this.governorSnapshot.incidentTokens()) {
            if ("under_siege".equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    /** Resident capacity not yet filled; non-negative. */
    public int housingHeadroom() {
        return Math.max(0, this.residentCapacity - this.assignedResidentCount);
    }
}
