package com.talhanation.bannermod.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TESTSAVELOAD-001: settlement snapshot save/load roundtrip coverage.
 *
 * <p>Each test builds a {@link SettlementSnapshot}, runs it through
 * {@code toTag} -> {@code fromTag}, and asserts that the deserialized snapshot equals the
 * original. Records auto-generate {@code equals} from the full component list, so
 * {@code assertEquals(original, restored)} fails the moment a field is added/removed without
 * the codec being updated. Each test additionally asserts every top-level snapshot field
 * individually so a regression points at the offending column rather than just "not equal".
 *
 * <p>Snapshot fields (must stay in sync with {@link SettlementSnapshot}):
 * <ul>
 *   <li>claimUuid</li>
 *   <li>anchorChunkX</li>
 *   <li>anchorChunkZ</li>
 *   <li>settlementFactionId</li>
 *   <li>lastRefreshedTick</li>
 *   <li>residentCapacity</li>
 *   <li>workplaceCapacity</li>
 *   <li>assignedWorkerCount</li>
 *   <li>assignedResidentCount</li>
 *   <li>unassignedWorkerCount</li>
 *   <li>missingWorkAreaAssignmentCount</li>
 *   <li>stockpileSummary</li>
 *   <li>marketState</li>
 *   <li>desiredGoodsSnapshot</li>
 *   <li>projectCandidateSnapshot</li>
 *   <li>tradeRouteHandoffSnapshot</li>
 *   <li>supplySignalState</li>
 *   <li>residents</li>
 *   <li>buildings</li>
 * </ul>
 */
class SettlementSnapshotRoundtripTest {

    @Test
    void emptySnapshotRoundTripsThroughTagCodec() {
        UUID claimUuid = UUID.randomUUID();
        SettlementSnapshot original = new SettlementSnapshot(
                claimUuid,
                0,
                0,
                null,
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
                List.of()
        );

        SettlementSnapshot restored = SettlementSnapshot.fromTag(original.toTag());

        assertEqualsFieldByField(original, restored);
        assertEquals(original, restored);
    }

    @Test
    void singleBuildingSnapshotRoundTripsThroughTagCodec() {
        UUID claimUuid = UUID.randomUUID();
        UUID buildingUuid = UUID.randomUUID();
        SettlementBuildingRecord building = new SettlementBuildingRecord(
                buildingUuid,
                "bannermod:storage_area",
                new BlockPos(8, 64, -16),
                UUID.randomUUID(),
                "blueguild",
                0,
                2,
                1,
                List.of(UUID.randomUUID()),
                true,
                3,
                81,
                true,
                false,
                List.of("food", "materials"),
                SettlementBuildingCategory.STORAGE,
                SettlementBuildingProfileSeed.STORAGE
        );

        SettlementSnapshot original = new SettlementSnapshot(
                claimUuid,
                3,
                -2,
                "blueguild",
                42L,
                0,
                2,
                1,
                0,
                1,
                0,
                SettlementStockpileSummary.empty(),
                SettlementMarketState.empty(),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(),
                List.of(building)
        );

        SettlementSnapshot restored = SettlementSnapshot.fromTag(original.toTag());

        assertEqualsFieldByField(original, restored);
        assertEquals(original, restored);
        assertEquals(1, restored.buildings().size());
        assertEquals(building, restored.buildings().get(0));
    }

    @Test
    void fullSnapshotRoundTripsAllNestedRecordsAndLists() {
        UUID claimUuid = UUID.randomUUID();
        UUID workerUuid = UUID.randomUUID();
        UUID workAreaUuid = UUID.randomUUID();
        UUID storageBuildingUuid = UUID.randomUUID();
        UUID marketBuildingUuid = UUID.randomUUID();

        SettlementResidentRecord governor = new SettlementResidentRecord(
                UUID.randomUUID(),
                SettlementResidentRole.GOVERNOR_RECRUIT,
                SettlementResidentScheduleSeed.GOVERNING,
                SettlementResidentScheduleWindowSeed.CIVIC_DAY,
                SettlementResidentRuntimeRoleState.GOVERNANCE,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentJobDefinition.defaultFor(
                        SettlementResidentRole.GOVERNOR_RECRUIT,
                        SettlementResidentRuntimeRoleState.GOVERNANCE,
                        SettlementResidentServiceContract.notServiceActor(),
                        null
                ),
                new SettlementResidentJobTargetSelectionState(
                        SettlementJobTargetSelectionMode.NONE, null, null
                ),
                SettlementResidentMode.SETTLEMENT_RESIDENT,
                UUID.randomUUID(),
                "blueguild",
                null,
                SettlementResidentAssignmentState.NOT_APPLICABLE,
                SettlementResidentRoleProfile.defaultFor(
                        SettlementResidentRole.GOVERNOR_RECRUIT,
                        SettlementResidentRuntimeRoleState.GOVERNANCE,
                        SettlementResidentMode.SETTLEMENT_RESIDENT,
                        SettlementResidentAssignmentState.NOT_APPLICABLE
                )
        );

        SettlementResidentRecord worker = new SettlementResidentRecord(
                workerUuid,
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                new SettlementResidentServiceContract(
                        SettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                        workAreaUuid,
                        "bannermod:crop_area"
                ),
                new SettlementResidentJobDefinition(
                        SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR,
                        workAreaUuid,
                        "bannermod:crop_area",
                        SettlementBuildingCategory.FOOD,
                        SettlementBuildingProfileSeed.FOOD_PRODUCTION
                ),
                new SettlementResidentJobTargetSelectionState(
                        SettlementJobTargetSelectionMode.SERVICE_BUILDING, null, null
                ),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                workAreaUuid,
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING,
                SettlementResidentRoleProfile.defaultFor(
                        SettlementResidentRole.CONTROLLED_WORKER,
                        SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                        SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                )
        );

        SettlementBuildingRecord storage = new SettlementBuildingRecord(
                storageBuildingUuid,
                "bannermod:storage_area",
                new BlockPos(2, 64, 4),
                UUID.randomUUID(),
                "blueguild",
                0,
                2,
                1,
                List.of(workerUuid),
                true,
                4,
                144,
                true,
                true,
                List.of("food", "wood"),
                SettlementBuildingCategory.STORAGE,
                SettlementBuildingProfileSeed.STORAGE
        );

        SettlementBuildingRecord market = new SettlementBuildingRecord(
                marketBuildingUuid,
                "bannermod:market_area",
                new BlockPos(20, 64, 8),
                UUID.randomUUID(),
                "blueguild",
                0,
                3,
                2,
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                false,
                0,
                0,
                false,
                false,
                List.of(),
                SettlementBuildingCategory.MARKET,
                SettlementBuildingProfileSeed.MARKET
        );

        SettlementBuildingRecord crop = new SettlementBuildingRecord(
                workAreaUuid,
                "bannermod:crop_area",
                new BlockPos(-12, 64, 6),
                UUID.randomUUID(),
                "blueguild",
                0,
                1,
                1,
                List.of(workerUuid),
                false,
                0,
                0,
                false,
                false,
                List.of(),
                SettlementBuildingCategory.FOOD,
                SettlementBuildingProfileSeed.FOOD_PRODUCTION
        );

        SettlementStockpileSummary stockpile = new SettlementStockpileSummary(
                1, 4, 144, 1, 1, List.of("food", "wood")
        );
        SettlementMarketState marketState = new SettlementMarketState(
                1,
                1,
                64,
                32,
                1,
                1,
                List.of(new SettlementMarketRecord(marketBuildingUuid, "Central Market", true, 64, 32)),
                List.of(new SettlementSellerDispatchRecord(
                        workerUuid, marketBuildingUuid, "Central Market", SettlementSellerDispatchState.READY
                ))
        );
        SettlementDesiredGoodsSnapshot desiredGoodsSnapshot = new SettlementDesiredGoodsSnapshot(
                List.of(
                        new SettlementDesiredGoodSnapshot("food", 3),
                        new SettlementDesiredGoodSnapshot("wood", 1)
                )
        );
        SettlementProjectCandidateSnapshot projectCandidateSnapshot = new SettlementProjectCandidateSnapshot(
                "expand_storage",
                SettlementBuildingProfileSeed.STORAGE,
                5,
                true,
                true,
                List.of("supply_pressure", "governor_priority")
        );
        SettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot = new SettlementTradeRouteHandoffSnapshot(
                1,
                1,
                1,
                1,
                2,
                7,
                List.of(new SettlementDesiredGoodSnapshot("food", 3)),
                List.of(new SettlementSellerDispatchRecord(
                        workerUuid, marketBuildingUuid, "Central Market", SettlementSellerDispatchState.READY
                )),
                List.of("port_open", "route_authored")
        );
        SettlementSupplySignalState supplySignalState = new SettlementSupplySignalState(
                2,
                1,
                4,
                3,
                List.of(
                        new SettlementSupplySignal("food", 10, 6, 4, 3),
                        new SettlementSupplySignal("wood", 5, 5, 0, 0)
                )
        );

        SettlementSnapshot original = new SettlementSnapshot(
                claimUuid,
                7,
                -3,
                "blueguild",
                12345L,
                4,
                6,
                3,
                2,
                1,
                1,
                stockpile,
                marketState,
                desiredGoodsSnapshot,
                projectCandidateSnapshot,
                tradeRouteHandoffSnapshot,
                supplySignalState,
                List.of(governor, worker),
                List.of(storage, market, crop)
        );

        SettlementSnapshot restored = SettlementSnapshot.fromTag(original.toTag());

        assertEqualsFieldByField(original, restored);
        assertEquals(original, restored);
        assertEquals(2, restored.residents().size());
        assertEquals(3, restored.buildings().size());
    }

    /**
     * Edge case: tag is structurally valid but missing the optional sub-tags and contains a
     * present-but-empty residents list. {@code fromTag} must populate empty defaults for the
     * sub-records, preserve the required scalars/UUID fields, and return an empty residents
     * list — and the resulting snapshot must roundtrip a second time without drift.
     */
    @Test
    void corruptedButValidEdgeCaseRoundTripsWithDefaults() {
        UUID claimUuid = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ClaimUuid", claimUuid);
        tag.putInt("AnchorChunkX", 1);
        tag.putInt("AnchorChunkZ", -1);
        tag.putLong("LastRefreshedTick", 99L);
        tag.putInt("ResidentCapacity", 4);
        tag.putInt("WorkplaceCapacity", 2);
        tag.putInt("AssignedWorkerCount", 1);
        tag.putInt("AssignedResidentCount", 2);
        tag.putInt("UnassignedWorkerCount", 1);
        tag.putInt("MissingWorkAreaAssignmentCount", 0);
        // Optional sub-tags (StockpileSummary, MarketState, DesiredGoodsSnapshot,
        // ProjectCandidateSnapshot, TradeRouteHandoffSnapshot, SupplySignalState, SettlementFactionId)
        // intentionally omitted.
        // Residents: present but empty.
        tag.put("Residents", new ListTag());
        // One realistic building so the buildings list is not also empty.
        ListTag buildings = new ListTag();
        SettlementBuildingRecord onlyBuilding = new SettlementBuildingRecord(
                UUID.randomUUID(),
                "bannermod:storage_area",
                new BlockPos(0, 64, 0),
                null,
                "blueguild",
                0,
                1,
                0,
                List.of(),
                true,
                1,
                27,
                false,
                false,
                List.of("food"),
                SettlementBuildingCategory.STORAGE,
                SettlementBuildingProfileSeed.STORAGE
        );
        buildings.add(onlyBuilding.toTag());
        tag.put("Buildings", buildings);

        SettlementSnapshot fromMissing = SettlementSnapshot.fromTag(tag);

        assertEquals(claimUuid, fromMissing.claimUuid());
        assertEquals(1, fromMissing.anchorChunkX());
        assertEquals(-1, fromMissing.anchorChunkZ());
        assertEquals(null, fromMissing.settlementFactionId());
        assertEquals(99L, fromMissing.lastRefreshedTick());
        assertEquals(4, fromMissing.residentCapacity());
        assertEquals(2, fromMissing.workplaceCapacity());
        assertEquals(1, fromMissing.assignedWorkerCount());
        assertEquals(2, fromMissing.assignedResidentCount());
        assertEquals(1, fromMissing.unassignedWorkerCount());
        assertEquals(0, fromMissing.missingWorkAreaAssignmentCount());
        assertEquals(SettlementStockpileSummary.empty(), fromMissing.stockpileSummary());
        assertEquals(SettlementMarketState.empty(), fromMissing.marketState());
        assertEquals(SettlementDesiredGoodsSnapshot.empty(), fromMissing.desiredGoodsSnapshot());
        assertEquals(SettlementProjectCandidateSnapshot.empty(), fromMissing.projectCandidateSnapshot());
        assertEquals(SettlementTradeRouteHandoffSnapshot.empty(), fromMissing.tradeRouteHandoffSnapshot());
        assertEquals(SettlementSupplySignalState.empty(), fromMissing.supplySignalState());
        assertTrue(fromMissing.residents().isEmpty());
        assertEquals(1, fromMissing.buildings().size());
        assertEquals(onlyBuilding, fromMissing.buildings().get(0));

        // Second pass: roundtrip the snapshot we just hydrated to confirm the codec stays
        // stable across writes once the defaults have materialized.
        SettlementSnapshot rehydrated = SettlementSnapshot.fromTag(fromMissing.toTag());
        assertEqualsFieldByField(fromMissing, rehydrated);
        assertEquals(fromMissing, rehydrated);
    }

    // --- helpers --------------------------------------------------------------------------

    /**
     * Asserts every snapshot record component matches between {@code expected} and
     * {@code actual}. Mirrors the field list in {@link SettlementSnapshot}; if a
     * field is added there, this helper must be updated and the test will fail until it is.
     */
    private static void assertEqualsFieldByField(SettlementSnapshot expected,
                                                 SettlementSnapshot actual) {
        assertEquals(expected.claimUuid(), actual.claimUuid(), "claimUuid");
        assertEquals(expected.anchorChunkX(), actual.anchorChunkX(), "anchorChunkX");
        assertEquals(expected.anchorChunkZ(), actual.anchorChunkZ(), "anchorChunkZ");
        assertEquals(expected.settlementFactionId(), actual.settlementFactionId(), "settlementFactionId");
        assertEquals(expected.lastRefreshedTick(), actual.lastRefreshedTick(), "lastRefreshedTick");
        assertEquals(expected.residentCapacity(), actual.residentCapacity(), "residentCapacity");
        assertEquals(expected.workplaceCapacity(), actual.workplaceCapacity(), "workplaceCapacity");
        assertEquals(expected.assignedWorkerCount(), actual.assignedWorkerCount(), "assignedWorkerCount");
        assertEquals(expected.assignedResidentCount(), actual.assignedResidentCount(), "assignedResidentCount");
        assertEquals(expected.unassignedWorkerCount(), actual.unassignedWorkerCount(), "unassignedWorkerCount");
        assertEquals(expected.missingWorkAreaAssignmentCount(), actual.missingWorkAreaAssignmentCount(), "missingWorkAreaAssignmentCount");
        assertEquals(expected.stockpileSummary(), actual.stockpileSummary(), "stockpileSummary");
        assertEquals(expected.marketState(), actual.marketState(), "marketState");
        assertEquals(expected.desiredGoodsSnapshot(), actual.desiredGoodsSnapshot(), "desiredGoodsSnapshot");
        assertEquals(expected.projectCandidateSnapshot(), actual.projectCandidateSnapshot(), "projectCandidateSnapshot");
        assertEquals(expected.tradeRouteHandoffSnapshot(), actual.tradeRouteHandoffSnapshot(), "tradeRouteHandoffSnapshot");
        assertEquals(expected.supplySignalState(), actual.supplySignalState(), "supplySignalState");
        assertEquals(expected.residents(), actual.residents(), "residents");
        assertEquals(expected.buildings(), actual.buildings(), "buildings");
    }
}
