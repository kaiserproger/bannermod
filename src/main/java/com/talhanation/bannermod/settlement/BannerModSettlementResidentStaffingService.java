package com.talhanation.bannermod.settlement;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class BannerModSettlementResidentStaffingService {

    private BannerModSettlementResidentStaffingService() {
    }

    static StaffingResult apply(List<BannerModSettlementResidentRecord> residents,
                                List<BannerModSettlementBuildingRecord> buildings,
                                BannerModSettlementMarketState marketState,
                                Set<UUID> localBuildingUuids) {
        List<BannerModSettlementResidentRecord> staffedResidents = BannerModSettlementSnapshotRuntime.applyResidentAssignmentSemantics(
                residents,
                localBuildingUuids
        );
        staffedResidents = BannerModSettlementSnapshotRuntime.applyResidentServiceContracts(staffedResidents, buildings);
        staffedResidents = BannerModSettlementSnapshotRuntime.applyResidentJobDefinitions(staffedResidents, buildings);
        List<BannerModSettlementBuildingRecord> staffedBuildings = BannerModSettlementSnapshotRuntime.applyAssignedResidents(buildings, staffedResidents);
        BannerModSettlementMarketState staffedMarketState = BannerModSettlementSnapshotRuntime.applySellerDispatchSeed(
                marketState,
                staffedResidents,
                staffedBuildings
        );
        staffedResidents = BannerModSettlementSnapshotRuntime.applyResidentJobTargetSelectionStates(staffedResidents, staffedMarketState);
        return new StaffingResult(staffedResidents, staffedBuildings, staffedMarketState);
    }

    record StaffingResult(List<BannerModSettlementResidentRecord> residents,
                          List<BannerModSettlementBuildingRecord> buildings,
                          BannerModSettlementMarketState marketState) {
    }
}
