package com.talhanation.bannermod.settlement;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class SettlementResidentStaffingService {

    private SettlementResidentStaffingService() {
    }

    static StaffingResult apply(List<SettlementResidentRecord> residents,
                                List<SettlementBuildingRecord> buildings,
                                SettlementMarketState marketState,
                                Set<UUID> localBuildingUuids) {
        List<SettlementResidentRecord> staffedResidents = SettlementSnapshotRuntime.applyResidentAssignmentSemantics(
                residents,
                localBuildingUuids
        );
        staffedResidents = SettlementSnapshotRuntime.applyResidentServiceContracts(staffedResidents, buildings);
        staffedResidents = SettlementSnapshotRuntime.applyResidentJobDefinitions(staffedResidents, buildings);
        List<SettlementBuildingRecord> staffedBuildings = SettlementSnapshotRuntime.applyAssignedResidents(buildings, staffedResidents);
        SettlementMarketState staffedMarketState = SettlementSnapshotRuntime.applySellerDispatchSeed(
                marketState,
                staffedResidents,
                staffedBuildings
        );
        staffedResidents = SettlementSnapshotRuntime.applyResidentJobTargetSelectionStates(staffedResidents, staffedMarketState);
        return new StaffingResult(staffedResidents, staffedBuildings, staffedMarketState);
    }

    record StaffingResult(List<SettlementResidentRecord> residents,
                          List<SettlementBuildingRecord> buildings,
                          SettlementMarketState marketState) {
    }
}
