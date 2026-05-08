package com.talhanation.bannermod.settlement.household;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketState;
import com.talhanation.bannermod.settlement.BannerModSettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementStockpileSummary;
import com.talhanation.bannermod.settlement.BannerModSettlementSupplySignalState;
import com.talhanation.bannermod.settlement.BannerModSettlementTradeRouteHandoffSnapshot;
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

        BannerModSettlementSnapshot snapshot = snapshot(List.of(
                building(currentHome, BannerModSettlementBuildingProfileSeed.GENERAL, 2),
                building(fullHome, BannerModSettlementBuildingProfileSeed.GENERAL, 1),
                building(freeHome, BannerModSettlementBuildingProfileSeed.GENERAL, 2),
                building(UUID.randomUUID(), BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, 3)
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

        BannerModSettlementSnapshot snapshot = snapshot(List.of(
                building(fullHome, BannerModSettlementBuildingProfileSeed.GENERAL, 1),
                building(shelter, BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, 1)
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

    private static BannerModSettlementSnapshot snapshot(List<BannerModSettlementBuildingRecord> buildings) {
        return new BannerModSettlementSnapshot(
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
                BannerModSettlementStockpileSummary.empty(),
                BannerModSettlementMarketState.empty(),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                List.of(),
                buildings
        );
    }

    private static BannerModSettlementBuildingRecord building(UUID buildingUuid,
                                                              BannerModSettlementBuildingProfileSeed profileSeed,
                                                              int residentCapacity) {
        return new BannerModSettlementBuildingRecord(
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
