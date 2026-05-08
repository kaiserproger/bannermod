package com.talhanation.bannermod.settlement.household;

import com.talhanation.bannermod.settlement.SettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModHomeAssignmentAdvisorTest {

    @Test
    void prefersHousingCategoryWithSpareCapacityAndSkipsCurrentOrFullHomes() {
        UUID residentUuid = UUID.randomUUID();
        UUID currentHome = UUID.randomUUID();
        UUID fullHome = UUID.randomUUID();
        UUID freeHome = UUID.randomUUID();
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        runtime.assign(residentUuid, currentHome, HomePreference.ASSIGNED, 0L);
        runtime.assign(UUID.randomUUID(), fullHome, HomePreference.ASSIGNED, 0L);

        SettlementSnapshot snapshot = snapshot(List.of(
                building(currentHome, SettlementBuildingProfileSeed.GENERAL, 2),
                building(fullHome, SettlementBuildingProfileSeed.GENERAL, 1),
                building(freeHome, SettlementBuildingProfileSeed.GENERAL, 2),
                building(UUID.randomUUID(), SettlementBuildingProfileSeed.FOOD_PRODUCTION, 3)
        ));

        assertEquals(freeHome, BannerModHomeAssignmentAdvisor.pickHomeBuilding(residentUuid, snapshot, runtime).orElseThrow());
    }

    @Test
    void fallsBackToAnyCategoryWhenNoGeneralHousingHasCapacity() {
        UUID residentUuid = UUID.randomUUID();
        UUID fullHome = UUID.randomUUID();
        UUID shelter = UUID.randomUUID();
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        runtime.assign(UUID.randomUUID(), fullHome, HomePreference.ASSIGNED, 0L);

        SettlementSnapshot snapshot = snapshot(List.of(
                building(fullHome, SettlementBuildingProfileSeed.GENERAL, 1),
                building(shelter, SettlementBuildingProfileSeed.FOOD_PRODUCTION, 1)
        ));

        assertEquals(shelter, BannerModHomeAssignmentAdvisor.pickHomeBuilding(residentUuid, snapshot, runtime).orElseThrow());
    }

    @Test
    void nullAndEmptyInputsReturnEmpty() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();

        assertTrue(BannerModHomeAssignmentAdvisor.pickHomeBuilding(null, snapshot(List.of()), runtime).isEmpty());
        assertTrue(BannerModHomeAssignmentAdvisor.pickHomeBuilding(UUID.randomUUID(), null, runtime).isEmpty());
        assertTrue(BannerModHomeAssignmentAdvisor.pickHomeBuilding(UUID.randomUUID(), snapshot(List.of()), runtime).isEmpty());
    }

    private static SettlementSnapshot snapshot(List<SettlementBuildingRecord> buildings) {
        return new SettlementSnapshot(
                UUID.randomUUID(),
                0,
                0,
                "blueguild",
                0L,
                0,
                0,
                0,
                0,
                0,
                0,
                SettlementStockpileSummary.empty(),
                SettlementMarketState.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(),
                buildings
        );
    }

    private static SettlementBuildingRecord building(UUID buildingUuid,
                                                              SettlementBuildingProfileSeed profileSeed,
                                                              int residentCapacity) {
        return new SettlementBuildingRecord(
                buildingUuid,
                "bannermod:test_" + profileSeed.name().toLowerCase(),
                BlockPos.ZERO,
                null,
                null,
                residentCapacity,
                0,
                0,
                List.of(),
                false,
                0,
                0,
                false,
                false,
                List.of(),
                profileSeed.category(),
                profileSeed
        );
    }
}
