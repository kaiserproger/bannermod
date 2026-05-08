package com.talhanation.bannermod.settlement.workorder;

import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementWorkOrderPublisherRegistryTest {

    @Test
    void matchesBuildingTypeAcceptsBareAndNamespacedIds() {
        SettlementBuildingRecord namespaced = building("bannermod:crop_area");
        SettlementBuildingRecord bare = building("crop_area");

        assertTrue(SettlementWorkOrderPublisherRegistry.matchesBuildingType(namespaced, "crop_area"));
        assertTrue(SettlementWorkOrderPublisherRegistry.matchesBuildingType(bare, "crop_area"));
    }

    @Test
    void matchesBuildingTypeRejectsDifferentTypeOrInvalidInput() {
        SettlementBuildingRecord building = building("bannermod:mining_area");

        assertFalse(SettlementWorkOrderPublisherRegistry.matchesBuildingType(building, "crop_area"));
        assertFalse(SettlementWorkOrderPublisherRegistry.matchesBuildingType(null, "crop_area"));
        assertFalse(SettlementWorkOrderPublisherRegistry.matchesBuildingType(building, null));
    }

    private static SettlementBuildingRecord building(String typeId) {
        return new SettlementBuildingRecord(
                UUID.randomUUID(),
                typeId,
                null,
                null,
                null,
                0,
                1,
                0,
                List.of()
        );
    }
}
