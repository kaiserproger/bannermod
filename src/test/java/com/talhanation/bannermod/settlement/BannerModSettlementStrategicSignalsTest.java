package com.talhanation.bannermod.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementStrategicSignalsTest {
    @Test
    void classifiesFoodAndStorageAsSurplusHubWithWarObjective() {
        SettlementStrategicSignals signals = SettlementStrategicSignals.fromSnapshot(snapshot(
                new SettlementStockpileSummary(1, 2, 54, 0, 0, List.of()),
                SettlementMarketState.empty(),
                List.of(
                        building("bannermod:crop_area", SettlementBuildingProfileSeed.FOOD_PRODUCTION),
                        building("bannermod:storage_area", SettlementBuildingProfileSeed.STORAGE)
                )
        ));

        assertEquals("surplus_hub", signals.roleId());
        assertEquals("landlocked", signals.routeCostId());
        assertEquals("preserved_food", signals.specializationId());
        assertTrue(signals.logisticsObjectiveIds().contains("surplus_store"));
        assertTrue(signals.loyaltyPressureIds().contains("isolated_supply"));
    }

    @Test
    void waterAccessBecomesWaterGateAndCheapRoute() {
        SettlementStrategicSignals signals = SettlementStrategicSignals.fromSnapshot(snapshot(
                new SettlementStockpileSummary(1, 2, 54, 1, 1, List.of()),
                new SettlementMarketState(1, 1, 27, 20, 0, 0, List.of(), List.of()),
                List.of(building("bannermod:storage_area", SettlementBuildingProfileSeed.STORAGE))
        ));

        assertEquals("water_gate", signals.roleId());
        assertEquals("water_advantaged", signals.routeCostId());
        assertTrue(signals.logisticsObjectiveIds().contains("water_gate"));
        assertTrue(signals.loyaltyPressureIds().isEmpty());
    }

    @Test
    void marketAndRouteStorageBecomeJunctionMarketWithSingleRoutePressure() {
        SettlementStrategicSignals signals = SettlementStrategicSignals.fromSnapshot(snapshot(
                new SettlementStockpileSummary(1, 1, 27, 1, 0, List.of()),
                new SettlementMarketState(1, 1, 9, 4, 0, 0, List.of(), List.of()),
                List.of(building("bannermod:storage_area", SettlementBuildingProfileSeed.STORAGE))
        ));

        assertEquals("junction_market", signals.roleId());
        assertEquals("single_route", signals.routeCostId());
        assertTrue(signals.logisticsObjectiveIds().contains("route_junction"));
        assertTrue(signals.loyaltyPressureIds().contains("dependent_on_single_route"));
    }

    @Test
    void fortifiedRouteStorageBecomesChokepointFort() {
        SettlementStrategicSignals signals = SettlementStrategicSignals.fromSnapshot(snapshot(
                new SettlementStockpileSummary(1, 1, 27, 1, 0, List.of()),
                SettlementMarketState.empty(),
                List.of(
                        building("bannermod:starter_fort", SettlementBuildingProfileSeed.GENERAL),
                        building("bannermod:storage_area", SettlementBuildingProfileSeed.STORAGE)
                )
        ));

        assertEquals("chokepoint_fort", signals.roleId());
        assertEquals("single_route", signals.routeCostId());
    }

    @Test
    void landlockedMaterialsBecomeWorkedGoodsSpecialization() {
        SettlementStrategicSignals signals = SettlementStrategicSignals.fromSnapshot(snapshot(
                new SettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                SettlementMarketState.empty(),
                List.of(
                        building("bannermod:mining_area", SettlementBuildingProfileSeed.MATERIAL_PRODUCTION),
                        building("bannermod:storage_area", SettlementBuildingProfileSeed.STORAGE)
                )
        ));

        assertEquals("worked_materials", signals.specializationId());
        assertTrue(signals.logisticsObjectiveIds().contains("stockpile"));
        assertTrue(signals.loyaltyPressureIds().contains("isolated_supply"));
    }

    @Test
    void nullSnapshotAndSparseOutpostUseFallbackSignals() {
        SettlementStrategicSignals emptySignals = SettlementStrategicSignals.fromSnapshot(null);
        SettlementStrategicSignals outpostSignals = SettlementStrategicSignals.fromSnapshot(snapshot(
                SettlementStockpileSummary.empty(),
                SettlementMarketState.empty(),
                List.of(building("bannermod:watchtower", SettlementBuildingProfileSeed.GENERAL))
        ));

        assertEquals("outpost", emptySignals.roleId());
        assertEquals("unknown", emptySignals.routeCostId());
        assertEquals("local_outpost", outpostSignals.roleId());
        assertTrue(outpostSignals.loyaltyPressureIds().contains("no_local_distribution"));
    }

    @Test
    void constructorNormalizesBlankSignalMetadataToStableFallbacks() {
        SettlementStrategicSignals signals = new SettlementStrategicSignals(
                " ",
                "",
                null,
                " ",
                "  ",
                null,
                null,
                null
        );

        assertEquals("outpost", signals.roleId());
        assertEquals("isolated", signals.routeCostId());
        assertEquals("none", signals.specializationId());
        assertEquals("Local outpost with no dominant logistics role yet.", signals.roleDescription());
        assertEquals("Supply depends on local stores or manual hauling.", signals.routeCostDescription());
        assertEquals("No landlocked specialty is visible yet.", signals.specializationDescription());
        assertTrue(signals.logisticsObjectiveIds().isEmpty());
        assertTrue(signals.loyaltyPressureIds().isEmpty());
    }

    private static SettlementSnapshot snapshot(SettlementStockpileSummary stockpileSummary,
                                                         SettlementMarketState marketState,
                                                         List<SettlementBuildingRecord> buildings) {
        ChunkPos anchor = new ChunkPos(0, 0);
        return new SettlementSnapshot(
                UUID.randomUUID(),
                anchor.x,
                anchor.z,
                null,
                0L,
                0,
                0,
                0,
                0,
                0,
                0,
                stockpileSummary,
                marketState,
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(),
                buildings
        );
    }

    private static SettlementBuildingRecord building(String typeId, SettlementBuildingProfileSeed profileSeed) {
        return new SettlementBuildingRecord(
                UUID.randomUUID(),
                typeId,
                BlockPos.ZERO,
                null,
                null,
                0,
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
