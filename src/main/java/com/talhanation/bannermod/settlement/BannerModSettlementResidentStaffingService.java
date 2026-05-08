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
        List<BannerModSettlementResidentRecord> staffedResidents = BannerModSettlementService.applyResidentAssignmentSemantics(
                residents,
                localBuildingUuids
        );
        staffedResidents = BannerModSettlementService.applyResidentServiceContracts(staffedResidents, buildings);
        staffedResidents = BannerModSettlementService.applyResidentJobDefinitions(staffedResidents, buildings);
        List<BannerModSettlementBuildingRecord> staffedBuildings = BannerModSettlementService.applyAssignedResidents(buildings, staffedResidents);
        BannerModSettlementMarketState staffedMarketState = BannerModSettlementService.applySellerDispatchSeed(
                marketState,
                staffedResidents,
                staffedBuildings
        );
        staffedResidents = BannerModSettlementService.applyResidentJobTargetSelectionStates(staffedResidents, staffedMarketState);
        return new StaffingResult(staffedResidents, staffedBuildings, staffedMarketState);
    }

    record StaffingResult(List<BannerModSettlementResidentRecord> residents,
                          List<BannerModSettlementBuildingRecord> buildings,
                          BannerModSettlementMarketState marketState) {
    }
}
