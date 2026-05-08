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
 * <p>Each test builds a {@link BannerModSettlementSnapshot}, runs it through
 * {@code toTag} -> {@code fromTag}, and asserts that the deserialized snapshot equals the
 * original. Records auto-generate {@code equals} from the full component list, so
 * {@code assertEquals(original, restored)} fails the moment a field is added/removed without
 * the codec being updated. Each test additionally asserts every top-level snapshot field
 * individually so a regression points at the offending column rather than just "not equal".
 *
 * <p>Snapshot fields (must stay in sync with {@link BannerModSettlementSnapshot}):
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
class BannerModSettlementSnapshotRoundtripTest {

    @Test
    void emptySnapshotRoundTripsThroughTagCodec() {
        UUID claimUuid = UUID.randomUUID();
        BannerModSettlementSnapshot original = new BannerModSettlementSnapshot(
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
                BannerModSettlementStockpileSummary.empty(),
                BannerModSettlementMarketState.empty(),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                List.of(),
                List.of()
        );

        BannerModSettlementSnapshot restored = BannerModSettlementSnapshot.fromTag(original.toTag());

        assertEqualsFieldByField(original, restored);
        assertEquals(original, restored);
    }

    @Test
    void singleBuildingSnapshotRoundTripsThroughTagCodec() {
        UUID claimUuid = UUID.randomUUID();
        UUID buildingUuid = UUID.randomUUID();
        BannerModSettlementBuildingRecord building = new BannerModSettlementBuildingRecord(
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
                BannerModSettlementBuildingCategory.STORAGE,
                BannerModSettlementBuildingProfileSeed.STORAGE
        );

        BannerModSettlementSnapshot original = new BannerModSettlementSnapshot(
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
                BannerModSettlementStockpileSummary.empty(),
                BannerModSettlementMarketState.empty(),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                BannerModSettlementProjectCandidateSnapshot.empty(),
                BannerModSettlementTradeRouteHandoffSnapshot.empty(),
                BannerModSettlementSupplySignalState.empty(),
                List.of(),
                List.of(building)
        );

        BannerModSettlementSnapshot restored = BannerModSettlementSnapshot.fromTag(original.toTag());

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

        BannerModSettlementResidentRecord governor = new BannerModSettlementResidentRecord(
                UUID.randomUUID(),
                BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                BannerModSettlementResidentScheduleSeed.GOVERNING,
                BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY,
                BannerModSettlementResidentRuntimeRoleState.GOVERNANCE,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentJobDefinition.defaultFor(
                        BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                        BannerModSettlementResidentRuntimeRoleState.GOVERNANCE,
                        BannerModSettlementResidentServiceContract.notServiceActor(),
                        null
                ),
                new BannerModSettlementResidentJobTargetSelectionState(
                        BannerModSettlementJobTargetSelectionMode.NONE, null, null
                ),
                BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                UUID.randomUUID(),
                "blueguild",
                null,
                BannerModSettlementResidentAssignmentState.NOT_APPLICABLE,
                BannerModSettlementResidentRoleProfile.defaultFor(
                        BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                        BannerModSettlementResidentRuntimeRoleState.GOVERNANCE,
                        BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                        BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
                )
        );

        BannerModSettlementResidentRecord worker = new BannerModSettlementResidentRecord(
                workerUuid,
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY,
                BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR,
                new BannerModSettlementResidentServiceContract(
                        BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                        workAreaUuid,
                        "bannermod:crop_area"
                ),
                new BannerModSettlementResidentJobDefinition(
                        BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR,
                        workAreaUuid,
                        "bannermod:crop_area",
                        BannerModSettlementBuildingCategory.FOOD,
                        BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION
                ),
                new BannerModSettlementResidentJobTargetSelectionState(
                        BannerModSettlementJobTargetSelectionMode.SERVICE_BUILDING, null, null
                ),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                workAreaUuid,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING,
                BannerModSettlementResidentRoleProfile.defaultFor(
                        BannerModSettlementResidentRole.CONTROLLED_WORKER,
                        BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR,
                        BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                )
        );

        BannerModSettlementBuildingRecord storage = new BannerModSettlementBuildingRecord(
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
                BannerModSettlementBuildingCategory.STORAGE,
                BannerModSettlementBuildingProfileSeed.STORAGE
        );

        BannerModSettlementBuildingRecord market = new BannerModSettlementBuildingRecord(
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
                BannerModSettlementBuildingCategory.MARKET,
                BannerModSettlementBuildingProfileSeed.MARKET
        );

        BannerModSettlementBuildingRecord crop = new BannerModSettlementBuildingRecord(
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
                BannerModSettlementBuildingCategory.FOOD,
                BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION
        );

        BannerModSettlementStockpileSummary stockpile = new BannerModSettlementStockpileSummary(
                1, 4, 144, 1, 1, List.of("food", "wood")
        );
        BannerModSettlementMarketState marketState = new BannerModSettlementMarketState(
                1,
                1,
                64,
                32,
                1,
                1,
                List.of(new BannerModSettlementMarketRecord(marketBuildingUuid, "Central Market", true, 64, 32)),
                List.of(new BannerModSettlementSellerDispatchRecord(
                        workerUuid, marketBuildingUuid, "Central Market", BannerModSettlementSellerDispatchState.READY
                ))
        );
        BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot = new BannerModSettlementDesiredGoodsSnapshot(
                List.of(
                        new BannerModSettlementDesiredGoodSnapshot("food", 3),
                        new BannerModSettlementDesiredGoodSnapshot("wood", 1)
                )
        );
        BannerModSettlementProjectCandidateSnapshot projectCandidateSnapshot = new BannerModSettlementProjectCandidateSnapshot(
                "expand_storage",
                BannerModSettlementBuildingProfileSeed.STORAGE,
                5,
                true,
                true,
                List.of("supply_pressure", "governor_priority")
        );
        BannerModSettlementTradeRouteHandoffSnapshot tradeRouteHandoffSnapshot = new BannerModSettlementTradeRouteHandoffSnapshot(
                1,
                1,
                1,
                1,
                2,
                7,
                List.of(new BannerModSettlementDesiredGoodSnapshot("food", 3)),
                List.of(new BannerModSettlementSellerDispatchRecord(
                        workerUuid, marketBuildingUuid, "Central Market", BannerModSettlementSellerDispatchState.READY
                )),
                List.of("port_open", "route_authored")
        );
        BannerModSettlementSupplySignalState supplySignalState = new BannerModSettlementSupplySignalState(
                2,
                1,
                4,
                3,
                List.of(
                        new BannerModSettlementSupplySignal("food", 10, 6, 4, 3),
                        new BannerModSettlementSupplySignal("wood", 5, 5, 0, 0)
                )
        );

        BannerModSettlementSnapshot original = new BannerModSettlementSnapshot(
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

        BannerModSettlementSnapshot restored = BannerModSettlementSnapshot.fromTag(original.toTag());

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
        BannerModSettlementBuildingRecord onlyBuilding = new BannerModSettlementBuildingRecord(
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
                BannerModSettlementBuildingCategory.STORAGE,
                BannerModSettlementBuildingProfileSeed.STORAGE
        );
        buildings.add(onlyBuilding.toTag());
        tag.put("Buildings", buildings);

        BannerModSettlementSnapshot fromMissing = BannerModSettlementSnapshot.fromTag(tag);

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
        assertEquals(BannerModSettlementStockpileSummary.empty(), fromMissing.stockpileSummary());
        assertEquals(BannerModSettlementMarketState.empty(), fromMissing.marketState());
        assertEquals(BannerModSettlementDesiredGoodsSnapshot.empty(), fromMissing.desiredGoodsSnapshot());
        assertEquals(BannerModSettlementProjectCandidateSnapshot.empty(), fromMissing.projectCandidateSnapshot());
        assertEquals(BannerModSettlementTradeRouteHandoffSnapshot.empty(), fromMissing.tradeRouteHandoffSnapshot());
        assertEquals(BannerModSettlementSupplySignalState.empty(), fromMissing.supplySignalState());
        assertTrue(fromMissing.residents().isEmpty());
        assertEquals(1, fromMissing.buildings().size());
        assertEquals(onlyBuilding, fromMissing.buildings().get(0));

        // Second pass: roundtrip the snapshot we just hydrated to confirm the codec stays
        // stable across writes once the defaults have materialized.
        BannerModSettlementSnapshot rehydrated = BannerModSettlementSnapshot.fromTag(fromMissing.toTag());
        assertEqualsFieldByField(fromMissing, rehydrated);
        assertEquals(fromMissing, rehydrated);
    }

    // --- helpers --------------------------------------------------------------------------

    /**
     * Asserts every snapshot record component matches between {@code expected} and
     * {@code actual}. Mirrors the field list in {@link BannerModSettlementSnapshot}; if a
     * field is added there, this helper must be updated and the test will fail until it is.
     */
    private static void assertEqualsFieldByField(BannerModSettlementSnapshot expected,
                                                 BannerModSettlementSnapshot actual) {
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
