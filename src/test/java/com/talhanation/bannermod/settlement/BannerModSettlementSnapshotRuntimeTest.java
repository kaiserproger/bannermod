package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.settlement.bootstrap.SettlementRecord;
import com.talhanation.bannermod.settlement.bootstrap.SettlementStatus;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementSnapshotRuntimeTest {

    @Test
    void fixedScenarioSnapshotNbtMatchesBaselineByteForByte() {
        UUID claimUuid = UUID.fromString("00000000-0000-0000-0000-000000000501");
        SettlementSnapshot snapshot = SettlementSnapshot.create(
                claimUuid,
                new ChunkPos(3, -2),
                "blueguild"
        );

        CompoundTag expected = new CompoundTag();
        expected.putUUID("ClaimUuid", claimUuid);
        expected.putInt("AnchorChunkX", 3);
        expected.putInt("AnchorChunkZ", -2);
        expected.putString("SettlementFactionId", "blueguild");
        expected.putLong("LastRefreshedTick", 0L);
        expected.putInt("ResidentCapacity", 0);
        expected.putInt("WorkplaceCapacity", 0);
        expected.putInt("AssignedWorkerCount", 0);
        expected.putInt("AssignedResidentCount", 0);
        expected.putInt("UnassignedWorkerCount", 0);
        expected.putInt("MissingWorkAreaAssignmentCount", 0);
        expected.put("StockpileSummary", SettlementStockpileSummary.empty().toTag());
        expected.put("MarketState", SettlementMarketState.empty().toTag());
        expected.put("DesiredGoodsSeed", SettlementDesiredGoodsSnapshot.empty().toTag());
        expected.put("ProjectCandidateSeed", SettlementProjectCandidateSnapshot.empty().toTag());
        expected.put("TradeRouteHandoffSeed", SettlementTradeRouteHandoffSnapshot.empty().toTag());
        expected.put("SupplySignalState", SettlementSupplySignalState.empty().toTag());
        expected.put("Residents", new ListTag());
        expected.put("Buildings", new ListTag());

        assertEquals(expected, snapshot.toTag());
    }

    @Test
    void summarizesAuthoredStockpileSeedsFromBuildingRecords() {
        SettlementStockpileSummary summary = SettlementSnapshotRuntime.summarizeStockpiles(List.of(
                new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 2, 54, true, false, List.of("farmers", "merchants")),
                new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:storage_area", new BlockPos(10, 64, 10), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 1, 27, false, true, List.of("farmers")),
                new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:crop_area", new BlockPos(20, 64, 20), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 99, 99, true, true, List.of("ignored"))
        ));

        assertEquals(2, summary.storageBuildingCount());
        assertEquals(3, summary.containerCount());
        assertEquals(81, summary.slotCapacity());
        assertEquals(1, summary.routedStorageCount());
        assertEquals(1, summary.portEntrypointCount());
        assertEquals(List.of("farmers", "merchants"), summary.authoredStorageTypeIds());
    }

    @Test
    void mapsValidatedHouseStorageAndWorkplaceBuildingsIntoSnapshotRecords() {
        UUID ownerUuid = UUID.randomUUID();
        SettlementBuildingRecord houseRecord = SettlementSnapshotRuntime.fromValidatedBuildingFields(UUID.randomUUID(), BuildingType.HOUSE, new BlockPos(0, 64, 0), 3, ownerUuid);
        SettlementBuildingRecord storageRecord = SettlementSnapshotRuntime.fromValidatedBuildingFields(UUID.randomUUID(), BuildingType.STORAGE, new BlockPos(8, 64, 0), 2, ownerUuid);
        SettlementBuildingRecord farmRecord = SettlementSnapshotRuntime.fromValidatedBuildingFields(UUID.randomUUID(), BuildingType.FARM, new BlockPos(16, 64, 0), 1, ownerUuid);
        SettlementStockpileSummary summary = SettlementSnapshotRuntime.summarizeStockpiles(List.of(storageRecord));

        assertEquals("bannermod:validated_house", houseRecord.buildingTypeId());
        assertEquals(3, houseRecord.residentCapacity());
        assertEquals(ownerUuid, houseRecord.ownerUuid());
        assertEquals("bannermod:validated_storage", storageRecord.buildingTypeId());
        assertEquals(2, summary.containerCount());
        assertEquals(54, summary.slotCapacity());
        assertEquals(SettlementBuildingProfileSeed.STORAGE, storageRecord.buildingProfileSeed());
        assertEquals("bannermod:validated_farm", farmRecord.buildingTypeId());
        assertEquals(1, farmRecord.workplaceSlots());
        assertEquals(SettlementBuildingProfileSeed.FOOD_PRODUCTION, farmRecord.buildingProfileSeed());
    }

    @Test
    void validatedBuildingLookupUsesSettlementIdInsteadOfClaimId() {
        UUID settlementId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        SettlementRecord settlement = new SettlementRecord(
                settlementId,
                UUID.randomUUID(),
                "faction",
                claimId,
                Level.OVERWORLD,
                BlockPos.ZERO,
                BlockPos.ZERO,
                UUID.randomUUID(),
                SettlementStatus.ACTIVE,
                10L
        );
        ValidatedBuildingRecord matchingRecord = new ValidatedBuildingRecord(
                UUID.randomUUID(),
                settlementId,
                BuildingType.FARM,
                Level.OVERWORLD,
                new BlockPos(4, 64, 4),
                List.of(),
                new AABB(4, 64, 4, 8, 65, 8),
                null,
                4,
                80,
                1L,
                1L,
                0L
        );
        ValidatedBuildingRecord wrongRecord = new ValidatedBuildingRecord(
                UUID.randomUUID(),
                claimId,
                BuildingType.FARM,
                Level.OVERWORLD,
                new BlockPos(4, 64, 4),
                List.of(),
                new AABB(4, 64, 4, 8, 65, 8),
                null,
                4,
                80,
                1L,
                1L,
                0L
        );

        assertTrue(SettlementSnapshotRuntime.validatedBuildingBelongsToSettlement(settlement, matchingRecord));
        assertEquals(false, SettlementSnapshotRuntime.validatedBuildingBelongsToSettlement(settlement, wrongRecord));
    }

    @Test
    void mergesValidatedCapacityIntoLiveWorkAreaRecordUsingValidatedBuildingAuthority() {
        UUID liveWorkAreaUuid = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        BlockPos origin = new BlockPos(12, 64, 12);
        ValidatedBuildingRecord record = new ValidatedBuildingRecord(
                UUID.randomUUID(),
                settlementId,
                BuildingType.FARM,
                Level.OVERWORLD,
                origin,
                List.of(),
                new AABB(12, 64, 12, 20, 65, 20),
                null,
                4,
                100,
                1L,
                1L,
                0L
        );
        SettlementBuildingRecord liveRecord = new SettlementBuildingRecord(
                liveWorkAreaUuid,
                "bannermod:crop_area",
                origin,
                ownerUuid,
                "54ac1b4f-006f-4fcb-a48d-47f93a9f575a",
                0,
                1,
                0,
                List.of()
        );
        SettlementBuildingRecord expectedValidated = SettlementSnapshotRuntime.fromValidatedBuildingFields(
                liveWorkAreaUuid,
                BuildingType.FARM,
                origin,
                4,
                ownerUuid
        );

        SettlementBuildingRecord merged = SettlementSnapshotRuntime.mergeValidatedBuildingIntoLiveRecord(record, liveRecord);

        assertEquals(record.buildingId(), merged.buildingUuid());
        assertEquals("bannermod:crop_area", merged.buildingTypeId());
        assertEquals(expectedValidated.workplaceSlots(), merged.workplaceSlots());
        assertEquals(expectedValidated.buildingCategory(), merged.buildingCategory());
        assertEquals(expectedValidated.buildingProfileSeed(), merged.buildingProfileSeed());
        assertEquals(ownerUuid, merged.ownerUuid());
        assertEquals(liveRecord.teamId(), merged.teamId());
    }

    @Test
    void authoritativeWorkBindingUsesValidatedBuildingWhenPresent() {
        UUID validatedBuildingUuid = UUID.randomUUID();
        UUID liveAreaUuid = UUID.randomUUID();

        assertEquals(
                validatedBuildingUuid,
                SettlementSnapshotRuntime.authoritativeWorkBuildingBinding(
                        liveAreaUuid,
                        Map.of(liveAreaUuid, validatedBuildingUuid)
                )
        );
    }

    @Test
    void authoritativeWorkBindingFallsBackToCanonicalLiveAreaWithoutValidatedBuilding() {
        UUID liveAreaUuid = UUID.randomUUID();
        UUID canonicalLiveAreaUuid = UUID.randomUUID();

        assertEquals(
                canonicalLiveAreaUuid,
                SettlementSnapshotRuntime.authoritativeWorkBuildingBinding(
                        liveAreaUuid,
                        Map.of(liveAreaUuid, canonicalLiveAreaUuid)
                )
        );
    }

    @Test
    void ignoresLegacyValidatedBuildingAssignedCitizensOnReload() {
        UUID staleWorkerUuid = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("BuildingId", UUID.randomUUID());
        tag.putUUID("SettlementId", UUID.randomUUID());
        tag.putString("Type", BuildingType.FARM.name());
        tag.putString("Dimension", "minecraft:overworld");
        tag.putLong("AnchorPos", new BlockPos(16, 64, 0).asLong());
        tag.putString("State", "VALID");
        tag.putInt("Capacity", 1);
        tag.putInt("QualityScore", 1);
        ListTag legacyAssigned = new ListTag();
        CompoundTag legacyAssignedEntry = new CompoundTag();
        legacyAssignedEntry.putUUID("CitizenId", staleWorkerUuid);
        legacyAssigned.add(legacyAssignedEntry);
        tag.put("AssignedCitizenIds", legacyAssigned);

        ValidatedBuildingRecord reloaded = ValidatedBuildingRecord.fromTag(tag);
        SettlementBuildingRecord building = SettlementSnapshotRuntime.fromValidatedBuilding(reloaded, null);

        assertEquals(0, building.assignedWorkerCount());
        assertEquals(List.of(), building.assignedResidentUuids());
    }

    @Test
    void summarizesMarketStateIntoAggregateSeed() {
        List<SettlementMarketRecord> markets = List.of(
                new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9),
                new SettlementMarketRecord(UUID.randomUUID(), "East Gate", false, 18, 4)
        );

        SettlementMarketState marketState = SettlementSnapshotRuntime.summarizeMarketState(markets);

        assertEquals(2, marketState.marketCount());
        assertEquals(1, marketState.openMarketCount());
        assertEquals(45, marketState.totalStorageSlots());
        assertEquals(13, marketState.freeStorageSlots());
        assertEquals(0, marketState.sellerDispatchCount());
        assertEquals(0, marketState.readySellerDispatchCount());
        assertEquals(markets, marketState.markets());
        assertEquals(List.of(), marketState.sellerDispatches());
    }


    @Test
    void scheduleWindowSeedDefaultsFromScheduleAndRuntimeRole() {
        assertEquals(
                SettlementResidentScheduleWindowSeed.CIVIC_DAY,
                SettlementResidentScheduleWindowSeed.defaultFor(
                        SettlementResidentScheduleSeed.GOVERNING,
                        SettlementResidentRuntimeRoleState.GOVERNANCE
                )
        );
        assertEquals(
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentScheduleWindowSeed.defaultFor(
                        SettlementResidentScheduleSeed.ASSIGNED_WORK,
                        SettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT
                )
        );
        assertEquals(
                SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                SettlementResidentScheduleWindowSeed.defaultFor(
                        SettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                        SettlementResidentRuntimeRoleState.VILLAGE_LIFE
                )
        );
    }

    @Test
    void schedulePolicyDefaultsFromResidentSeeds() {
        SettlementResidentRoleProfile floatingProfile = SettlementResidentRoleProfile.defaultFor(
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentRuntimeRoleState.FLOATING_LABOR,
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                SettlementResidentAssignmentState.UNASSIGNED
        );

        assertEquals(
                SettlementResidentSchedulePolicySeed.GOVERNANCE_CIVIC,
                SettlementResidentSchedulePolicy.defaultFor(
                        SettlementResidentScheduleSeed.GOVERNING,
                        SettlementResidentScheduleWindowSeed.CIVIC_DAY,
                        SettlementResidentRuntimeRoleState.GOVERNANCE,
                        SettlementResidentRoleProfile.defaultFor(
                                SettlementResidentRole.GOVERNOR_RECRUIT,
                                SettlementResidentRuntimeRoleState.GOVERNANCE,
                                SettlementResidentMode.SETTLEMENT_RESIDENT,
                                SettlementResidentAssignmentState.NOT_APPLICABLE
                        )
                ).policySeed()
        );
        assertEquals(
                SettlementResidentSchedulePolicySeed.FLOATING_LABOR_FLEX,
                SettlementResidentSchedulePolicy.defaultFor(
                        SettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                        SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                        SettlementResidentRuntimeRoleState.FLOATING_LABOR,
                        floatingProfile
                ).policySeed()
        );
        assertEquals(
                SettlementResidentSchedulePolicySeed.ORPHANED_LABOR_DAY,
                SettlementResidentSchedulePolicy.defaultFor(
                        SettlementResidentScheduleSeed.ASSIGNED_WORK,
                        SettlementResidentScheduleWindowSeed.LABOR_DAY,
                        SettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT,
                        SettlementResidentRoleProfile.defaultFor(
                                SettlementResidentRole.CONTROLLED_WORKER,
                                SettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT,
                                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                                SettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING
                        )
                ).policySeed()
        );
    }

    @Test
    void projectsSellerDispatchSeedFromMarketServiceContracts() {
        UUID openMarketUuid = UUID.randomUUID();
        UUID closedMarketUuid = UUID.randomUUID();
        UUID cropAreaUuid = UUID.randomUUID();
        UUID readySellerUuid = UUID.randomUUID();
        UUID blockedSellerUuid = UUID.randomUUID();

        SettlementMarketState marketState = SettlementSnapshotRuntime.applySellerDispatchSeed(
                SettlementSnapshotRuntime.summarizeMarketState(List.of(
                        new SettlementMarketRecord(openMarketUuid, "Harbor Square", true, 27, 9),
                        new SettlementMarketRecord(closedMarketUuid, "East Gate", false, 18, 4)
                )),
                List.of(
                        new SettlementResidentRecord(readySellerUuid, SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, openMarketUuid, "bannermod:market_area"), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", openMarketUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new SettlementResidentRecord(blockedSellerUuid, SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, closedMarketUuid, "bannermod:market_area"), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", closedMarketUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, cropAreaUuid, "bannermod:crop_area"), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", cropAreaUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.SETTLEMENT_IDLE, SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, SettlementResidentRuntimeRoleState.FLOATING_LABOR, SettlementResidentServiceContract.notServiceActor(), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", null, SettlementResidentAssignmentState.UNASSIGNED)
                ),
                List.of(
                        new SettlementBuildingRecord(openMarketUuid, "bannermod:market_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(readySellerUuid), false, 0, 0, false, false, List.of()),
                        new SettlementBuildingRecord(closedMarketUuid, "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(blockedSellerUuid), false, 0, 0, false, false, List.of()),
                        new SettlementBuildingRecord(cropAreaUuid, "bannermod:crop_area", new BlockPos(16, 64, 16), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of())
                )
        );

        assertEquals(2, marketState.sellerDispatchCount());
        assertEquals(1, marketState.readySellerDispatchCount());
        assertEquals(List.of(
                new SettlementSellerDispatchRecord(readySellerUuid, openMarketUuid, "Harbor Square", SettlementSellerDispatchState.READY),
                new SettlementSellerDispatchRecord(blockedSellerUuid, closedMarketUuid, "East Gate", SettlementSellerDispatchState.MARKET_CLOSED)
        ), marketState.sellerDispatches());

        List<SettlementResidentRecord> residents = SettlementSnapshotRuntime.applyResidentJobTargetSelectionStates(
                List.of(
                        new SettlementResidentRecord(readySellerUuid, SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, openMarketUuid, "bannermod:market_area"), new SettlementResidentJobDefinition(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, openMarketUuid, "bannermod:market_area", SettlementBuildingCategory.MARKET, SettlementBuildingProfileSeed.MARKET), SettlementResidentJobTargetSelectionState.none(), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", openMarketUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, SettlementResidentRoleProfile.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING)),
                        new SettlementResidentRecord(blockedSellerUuid, SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, closedMarketUuid, "bannermod:market_area"), new SettlementResidentJobDefinition(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, closedMarketUuid, "bannermod:market_area", SettlementBuildingCategory.MARKET, SettlementBuildingProfileSeed.MARKET), SettlementResidentJobTargetSelectionState.none(), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", closedMarketUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, SettlementResidentRoleProfile.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING)),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, cropAreaUuid, "bannermod:crop_area"), new SettlementResidentJobDefinition(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, cropAreaUuid, "bannermod:crop_area", SettlementBuildingCategory.FOOD, SettlementBuildingProfileSeed.FOOD_PRODUCTION), SettlementResidentJobTargetSelectionState.none(), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", cropAreaUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, SettlementResidentRoleProfile.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING))
                ),
                marketState
        );

        assertEquals(SettlementJobTargetSelectionMode.SELLER_MARKET_DISPATCH, residents.get(0).jobTargetSelectionState().selectionMode());
        assertEquals(openMarketUuid, residents.get(0).jobTargetSelectionState().targetMarketUuid());
        assertEquals("Harbor Square", residents.get(0).jobTargetSelectionState().targetMarketName());
        assertEquals(SettlementJobTargetSelectionMode.SELLER_MARKET_CLOSED, residents.get(1).jobTargetSelectionState().selectionMode());
        assertEquals(closedMarketUuid, residents.get(1).jobTargetSelectionState().targetMarketUuid());
        assertEquals("East Gate", residents.get(1).jobTargetSelectionState().targetMarketName());
        assertEquals(SettlementJobTargetSelectionMode.SERVICE_BUILDING, residents.get(2).jobTargetSelectionState().selectionMode());
    }

    @Test
    void summarizesDesiredGoodsFromBuildingProfilesStockpileTypesAndMarkets() {
        List<SettlementBuildingRecord> buildings = List.of(
                new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:crop_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of()),
                new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:mining_area", new BlockPos(10, 64, 10), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of()),
                new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:build_area", new BlockPos(20, 64, 20), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of()),
                new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:market_area", new BlockPos(30, 64, 30), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())
        );

        SettlementDesiredGoodsSnapshot desiredGoodsSnapshot = SettlementSnapshotRuntime.summarizeDesiredGoods(
                buildings,
                new SettlementStockpileSummary(1, 2, 54, 1, 0, List.of("farmers", "merchants")),
                new SettlementMarketState(2, 1, 45, 13, 0, 0, List.of(
                        new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9),
                        new SettlementMarketRecord(UUID.randomUUID(), "East Gate", false, 18, 4)
                ), List.of())
        );

        assertEquals(List.of(
                new SettlementDesiredGoodSnapshot("food", 1),
                new SettlementDesiredGoodSnapshot("materials", 1),
                new SettlementDesiredGoodSnapshot("construction_materials", 1),
                new SettlementDesiredGoodSnapshot("market_goods", 3),
                new SettlementDesiredGoodSnapshot("storage_type:farmers", 1),
                new SettlementDesiredGoodSnapshot("storage_type:merchants", 1),
                new SettlementDesiredGoodSnapshot("trade_stock", 1)
        ), desiredGoodsSnapshot.desiredGoods());
    }

    @Test
    void summarizesTradeRouteHandoffSnapshotFromDispatchDemandAndRouteHints() {
        SettlementMarketState marketState = new SettlementMarketState(
                2,
                1,
                45,
                13,
                2,
                1,
                List.of(
                        new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9),
                        new SettlementMarketRecord(UUID.randomUUID(), "East Gate", false, 18, 4)
                ),
                List.of(
                        new SettlementSellerDispatchRecord(UUID.randomUUID(), UUID.randomUUID(), "Harbor Square", SettlementSellerDispatchState.READY),
                        new SettlementSellerDispatchRecord(UUID.randomUUID(), UUID.randomUUID(), "East Gate", SettlementSellerDispatchState.MARKET_CLOSED)
                )
        );

        SettlementDesiredGoodsSnapshot desiredGoodsSnapshot = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("market_goods", 3),
                new SettlementDesiredGoodSnapshot("trade_stock", 1),
                new SettlementDesiredGoodSnapshot("storage_type:merchants", 1)
        ));

        SettlementTradeRouteHandoffSnapshot handoffSnapshot = SettlementSnapshotRuntime.summarizeTradeRouteHandoffSnapshot(
                new SettlementStockpileSummary(2, 3, 81, 1, 1, List.of("farmers", "merchants")),
                marketState,
                desiredGoodsSnapshot,
                new SettlementSnapshotRuntime.ReservationSignalSeed(2, 24, Map.of("trade_stock", 24))
        );

        assertEquals(2, handoffSnapshot.sellerDispatchCount());
        assertEquals(1, handoffSnapshot.readySellerDispatchCount());
        assertEquals(1, handoffSnapshot.routedStorageCount());
        assertEquals(1, handoffSnapshot.portEntrypointCount());
        assertEquals(2, handoffSnapshot.activeReservationCount());
        assertEquals(24, handoffSnapshot.reservedUnitCount());
        assertEquals(desiredGoodsSnapshot.desiredGoods(), handoffSnapshot.desiredGoods());
        assertEquals(marketState.sellerDispatches(), handoffSnapshot.sellerDispatches());
    }

    @Test
    void summarizesSupplySignalsFromDesiredGoodsCoverageAndReservationHints() {
        UUID marketUuid = UUID.randomUUID();
        UUID cropAreaUuid = UUID.randomUUID();
        UUID mineUuid = UUID.randomUUID();

        SettlementSupplySignalState supplySignalState = SettlementSnapshotRuntime.summarizeSupplySignals(
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("food", 2),
                        new SettlementDesiredGoodSnapshot("materials", 1),
                        new SettlementDesiredGoodSnapshot("construction_materials", 1),
                        new SettlementDesiredGoodSnapshot("market_goods", 3),
                        new SettlementDesiredGoodSnapshot("storage_type:farmers", 1),
                        new SettlementDesiredGoodSnapshot("trade_stock", 1)
                )),
                new SettlementStockpileSummary(1, 2, 54, 1, 1, List.of("farmers")),
                new SettlementMarketState(
                        1,
                        1,
                        27,
                        9,
                        2,
                        1,
                        List.of(new SettlementMarketRecord(marketUuid, "Harbor Square", true, 27, 9)),
                        List.of(
                                new SettlementSellerDispatchRecord(UUID.randomUUID(), marketUuid, "Harbor Square", SettlementSellerDispatchState.READY),
                                new SettlementSellerDispatchRecord(UUID.randomUUID(), marketUuid, "Harbor Square", SettlementSellerDispatchState.MARKET_CLOSED)
                        )
                ),
                List.of(
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, cropAreaUuid, "bannermod:crop_area"), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", cropAreaUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, mineUuid, "bannermod:mining_area"), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", mineUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.LOCAL_LABOR, SettlementResidentServiceContract.defaultFor(SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, marketUuid, "bannermod:market_area"), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", marketUuid, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING)
                ),
                List.of(
                        new SettlementBuildingRecord(cropAreaUuid, "bannermod:crop_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of()),
                        new SettlementBuildingRecord(mineUuid, "bannermod:mining_area", new BlockPos(10, 64, 10), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of()),
                        new SettlementBuildingRecord(marketUuid, "bannermod:market_area", new BlockPos(20, 64, 20), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of())
                ),
                SettlementSnapshotRuntime.ReservationSignalSeed.empty()
        );

        assertEquals(6, supplySignalState.signalCount());
        assertEquals(3, supplySignalState.shortageSignalCount());
        assertEquals(3, supplySignalState.shortageUnitCount());
        assertEquals(0, supplySignalState.reservationHintUnitCount());
        assertEquals(List.of(
                new SettlementSupplySignal("food", 2, 1, 1, 0),
                new SettlementSupplySignal("materials", 1, 1, 0, 0),
                new SettlementSupplySignal("construction_materials", 1, 0, 1, 0),
                new SettlementSupplySignal("market_goods", 3, 2, 1, 0),
                new SettlementSupplySignal("storage_type:farmers", 1, 1, 0, 0),
                new SettlementSupplySignal("trade_stock", 1, 2, 0, 0)
        ), supplySignalState.signals());
    }

    @Test
    void supplySignalsUseOnlySpecificReservationHints() {
        SettlementSupplySignalState supplySignalState = SettlementSnapshotRuntime.summarizeSupplySignals(
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("market_goods", 3),
                        new SettlementDesiredGoodSnapshot("food", 2)
                )),
                SettlementStockpileSummary.empty(),
                SettlementMarketState.empty(),
                List.of(),
                List.of(),
                new SettlementSnapshotRuntime.ReservationSignalSeed(1, 12, Map.of("market_goods", 12))
        );

        assertEquals(12, supplySignalState.reservationHintUnitCount());
        assertEquals(new SettlementSupplySignal("market_goods", 3, 0, 3, 12), supplySignalState.signals().get(0));
        assertEquals(new SettlementSupplySignal("food", 2, 0, 2, 0), supplySignalState.signals().get(1));
    }

    @Test
    void summarizesReservationSignalSeedAndFeedsTradeAndMerchantHints() {
        UUID farmerStorageUuid = UUID.randomUUID();
        UUID merchantPortUuid = UUID.randomUUID();
        SettlementSnapshotRuntime.ReservationSignalSeed reservationSignalSeed = SettlementSnapshotRuntime.summarizeReservationSignalSeed(
                List.of(
                        new SettlementBuildingRecord(farmerStorageUuid, "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 1, 27, true, false, List.of("farmers")),
                        new SettlementBuildingRecord(merchantPortUuid, "bannermod:storage_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 1, 27, true, true, List.of("merchants"))
                ),
                List.of(new com.talhanation.bannermod.shared.logistics.BannerModLogisticsRoute(
                        UUID.fromString("00000000-0000-0000-0000-000000000510"),
                        new com.talhanation.bannermod.shared.logistics.BannerModLogisticsNodeRef(farmerStorageUuid),
                        new com.talhanation.bannermod.shared.logistics.BannerModLogisticsNodeRef(merchantPortUuid),
                        com.talhanation.bannermod.shared.logistics.BannerModLogisticsItemFilter.any(),
                        16,
                        com.talhanation.bannermod.shared.logistics.BannerModLogisticsPriority.NORMAL
                )),
                List.of(new com.talhanation.bannermod.shared.logistics.BannerModLogisticsReservation(
                        UUID.fromString("00000000-0000-0000-0000-000000000511"),
                        UUID.fromString("00000000-0000-0000-0000-000000000510"),
                        UUID.randomUUID(),
                        com.talhanation.bannermod.shared.logistics.BannerModLogisticsItemFilter.any(),
                        12,
                        120L
                ))
        );

        assertEquals(1, reservationSignalSeed.activeReservationCount());
        assertEquals(12, reservationSignalSeed.reservedUnitCount());
        assertEquals(12, reservationSignalSeed.reservationHintUnitsByGood().get("storage_type:farmers"));
        assertEquals(12, reservationSignalSeed.reservationHintUnitsByGood().get("storage_type:merchants"));
        assertEquals(12, reservationSignalSeed.reservationHintUnitsByGood().get("market_goods"));
        assertEquals(12, reservationSignalSeed.reservationHintUnitsByGood().get("trade_stock"));
    }

    @Test
    void summarizesProjectCandidateFromSettlementSeeds() {
        SettlementProjectCandidateSnapshot storageCandidate = SettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(
                        new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:crop_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of()),
                        new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())
                ),
                SettlementStockpileSummary.empty(),
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("food", 1),
                        new SettlementDesiredGoodSnapshot("market_goods", 2)
                )),
                new SettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                true,
                true
        );

        assertEquals("storage_foundation", storageCandidate.candidateId());
        assertEquals(SettlementBuildingProfileSeed.STORAGE, storageCandidate.targetBuildingProfileSeed());
        assertEquals(5, storageCandidate.priority());
        assertEquals(true, storageCandidate.governedSettlement());
        assertEquals(true, storageCandidate.claimedSettlement());
        assertEquals(List.of("storage_missing", "goods_pressure", "market_access_present"), storageCandidate.driverIds());

        SettlementProjectCandidateSnapshot foodCandidate = SettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(
                        new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 2, 54, true, false, List.of("farmers")),
                        new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())
                ),
                new SettlementStockpileSummary(1, 2, 54, 1, 0, List.of("farmers")),
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("food", 2),
                        new SettlementDesiredGoodSnapshot("market_goods", 1)
                )),
                new SettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                false,
                true
        );

        assertEquals("food_capacity_growth", foodCandidate.candidateId());
        assertEquals(SettlementBuildingProfileSeed.FOOD_PRODUCTION, foodCandidate.targetBuildingProfileSeed());
        assertEquals(3, foodCandidate.priority());
        assertEquals(List.of("food_demand", "storage_type:farmers"), foodCandidate.driverIds());
    }

    @Test
    void summarizesDesiredGoodsIncludesSeaTradeImportAndExportDrivers() {
        BannerModSeaTradeSummary.Summary seaTradeSummary = new BannerModSeaTradeSummary.Summary(
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"), 4),
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"), 2),
                List.of()
        );

        SettlementDesiredGoodsSnapshot desiredGoodsSnapshot = SettlementSnapshotRuntime.summarizeDesiredGoods(
                List.of(),
                SettlementStockpileSummary.empty(),
                SettlementMarketState.empty(),
                seaTradeSummary
        );

        assertEquals(List.of(
                new SettlementDesiredGoodSnapshot("sea_import:minecraft:iron_ingot", 2),
                new SettlementDesiredGoodSnapshot("sea_export:minecraft:wheat", 4)
        ), desiredGoodsSnapshot.desiredGoods());
    }

    @Test
    void summarizesSupplySignalsCountsSeaTradeMarketAndStorageCoverage() {
        SettlementDesiredGoodsSnapshot desiredGoodsSnapshot = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("storage_type:merchants", 1),
                new SettlementDesiredGoodSnapshot("market_goods", 2),
                new SettlementDesiredGoodSnapshot("trade_stock", 3),
                new SettlementDesiredGoodSnapshot("sea_import:minecraft:iron_ingot", 4),
                new SettlementDesiredGoodSnapshot("sea_export:minecraft:wheat", 5)
        ));
        BannerModSeaTradeSummary.Summary seaTradeSummary = new BannerModSeaTradeSummary.Summary(
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"), 5),
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"), 4),
                List.of()
        );

        SettlementSupplySignalState signals = SettlementSnapshotRuntime.summarizeSupplySignals(
                desiredGoodsSnapshot,
                new SettlementStockpileSummary(1, 1, 27, 0, 2, List.of("merchants")),
                new SettlementMarketState(1, 1, 27, 9, 2, 2, List.of(), List.of()),
                List.of(),
                List.of(),
                SettlementSnapshotRuntime.ReservationSignalSeed.empty(),
                seaTradeSummary
        );

        assertEquals(new SettlementSupplySignalState(
                5,
                0,
                0,
                0,
                List.of(
                        new SettlementSupplySignal("storage_type:merchants", 1, 1, 0, 0),
                        new SettlementSupplySignal("market_goods", 2, 2, 0, 0),
                        new SettlementSupplySignal("trade_stock", 3, 3, 0, 0),
                        new SettlementSupplySignal("sea_import:minecraft:iron_ingot", 4, 4, 0, 0),
                        new SettlementSupplySignal("sea_export:minecraft:wheat", 5, 5, 0, 0)
                )
        ), signals);
    }

    @Test
    void summarizesProjectCandidatePrefersMarketFoundationWhenDemandExistsWithoutMarket() {
        SettlementProjectCandidateSnapshot candidate = SettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(storageBuilding(false, false, List.of("merchants"))),
                new SettlementStockpileSummary(1, 1, 27, 0, 0, List.of("merchants")),
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("market_goods", 2)
                )),
                SettlementMarketState.empty(),
                true,
                false
        );

        assertEquals("market_foundation", candidate.candidateId());
        assertEquals(SettlementBuildingProfileSeed.MARKET, candidate.targetBuildingProfileSeed());
        assertEquals(4, candidate.priority());
        assertEquals(List.of("market_missing", "market_goods_demand", "stockpile_ready"), candidate.driverIds());
    }

    @Test
    void summarizesProjectCandidateRecoversClosedMarketsBeforeExpansion() {
        SettlementProjectCandidateSnapshot candidate = SettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(
                        storageBuilding(false, false, List.of()),
                        building("bannermod:market_area", SettlementBuildingProfileSeed.MARKET)
                ),
                new SettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                SettlementDesiredGoodsSnapshot.empty(),
                new SettlementMarketState(2, 1, 27, 9, 1, 1, List.of(
                        new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9),
                        new SettlementMarketRecord(UUID.randomUUID(), "East Gate", false, 18, 4)
                ), List.of()),
                false,
                false
        );

        assertEquals("market_recovery", candidate.candidateId());
        assertEquals(SettlementBuildingProfileSeed.MARKET, candidate.targetBuildingProfileSeed());
        assertEquals(List.of("closed_market_capacity", "seller_ready"), candidate.driverIds());
    }

    @Test
    void summarizesProjectCandidateUsesMaterialPressureWhenStorageAndMarketsExist() {
        SettlementProjectCandidateSnapshot candidate = SettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(
                        storageBuilding(false, false, List.of()),
                        building("bannermod:market_area", SettlementBuildingProfileSeed.MARKET)
                ),
                new SettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("materials", 2)
                )),
                new SettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                false,
                true
        );

        assertEquals("material_capacity_growth", candidate.candidateId());
        assertEquals(SettlementBuildingProfileSeed.MATERIAL_PRODUCTION, candidate.targetBuildingProfileSeed());
        assertEquals(List.of("materials_demand"), candidate.driverIds());
    }

    @Test
    void summarizesProjectCandidateUsesConstructionPressureAndCanSettleOnNone() {
        SettlementProjectCandidateSnapshot constructionCandidate = SettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(
                        storageBuilding(false, false, List.of()),
                        building("bannermod:market_area", SettlementBuildingProfileSeed.MARKET)
                ),
                new SettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("construction_materials", 1)
                )),
                new SettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                false,
                false
        );
        SettlementProjectCandidateSnapshot noneCandidate = SettlementSnapshotRuntime.summarizeProjectCandidate(
                List.of(
                        storageBuilding(false, false, List.of()),
                        building("bannermod:market_area", SettlementBuildingProfileSeed.MARKET),
                        building("bannermod:crop_area", SettlementBuildingProfileSeed.FOOD_PRODUCTION)
                ),
                new SettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                new SettlementDesiredGoodsSnapshot(List.of(
                        new SettlementDesiredGoodSnapshot("food", 1)
                )),
                new SettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new SettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                false,
                false
        );

        assertEquals("construction_capacity_growth", constructionCandidate.candidateId());
        assertEquals(SettlementBuildingProfileSeed.CONSTRUCTION, constructionCandidate.targetBuildingProfileSeed());
        assertEquals("none", noneCandidate.candidateId());
        assertEquals(0, noneCandidate.priority());
    }

    private static SettlementBuildingRecord building(String typeId,
                                                              SettlementBuildingProfileSeed profileSeed) {
        return new SettlementBuildingRecord(
                UUID.randomUUID(),
                typeId,
                BlockPos.ZERO,
                UUID.randomUUID(),
                "blueguild",
                0,
                1,
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

    private static SettlementBuildingRecord storageBuilding(boolean routed,
                                                                     boolean portEntrypoint,
                                                                     List<String> typeIds) {
        return new SettlementBuildingRecord(
                UUID.randomUUID(),
                "bannermod:storage_area",
                BlockPos.ZERO,
                UUID.randomUUID(),
                "blueguild",
                0,
                1,
                0,
                List.of(),
                true,
                1,
                27,
                routed,
                portEntrypoint,
                typeIds,
                SettlementBuildingProfileSeed.STORAGE.category(),
                SettlementBuildingProfileSeed.STORAGE
        );
    }
}
