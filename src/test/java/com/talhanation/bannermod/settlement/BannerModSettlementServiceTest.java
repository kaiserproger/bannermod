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
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModSettlementServiceTest {

    @Test
    void appliesResidentAssignmentSemanticsAndRollsAssignedWorkersIntoBuildings() {
        UUID localBuildingUuid = UUID.randomUUID();
        UUID assignedWorkerUuid = UUID.randomUUID();

        BannerModSettlementResidentStaffingService.StaffingResult staffing = BannerModSettlementResidentStaffingService.apply(
                List.of(
                        new BannerModSettlementResidentRecord(assignedWorkerUuid, BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.FLOATING_LABOR, BannerModSettlementResidentServiceContract.notServiceActor(), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", localBuildingUuid, BannerModSettlementResidentAssignmentState.UNASSIGNED),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE, BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, BannerModSettlementResidentRuntimeRoleState.FLOATING_LABOR, BannerModSettlementResidentServiceContract.notServiceActor(), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", null, BannerModSettlementResidentAssignmentState.UNASSIGNED),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.FLOATING_LABOR, BannerModSettlementResidentServiceContract.notServiceActor(), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", UUID.randomUUID(), BannerModSettlementResidentAssignmentState.UNASSIGNED),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.VILLAGER, BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE, BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, BannerModSettlementResidentRuntimeRoleState.VILLAGE_LIFE, BannerModSettlementResidentServiceContract.notServiceActor(), BannerModSettlementResidentMode.SETTLEMENT_RESIDENT, null, "blueguild", null, BannerModSettlementResidentAssignmentState.NOT_APPLICABLE)
                ),
                List.of(new BannerModSettlementBuildingRecord(localBuildingUuid, "bannermod:crop_area", new BlockPos(12, 64, 12), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())),
                BannerModSettlementMarketState.empty(),
                Set.of(localBuildingUuid)
        );
        List<BannerModSettlementResidentRecord> residents = staffing.residents();
        List<BannerModSettlementBuildingRecord> buildings = staffing.buildings();

        assertEquals(BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, residents.get(0).assignmentState());
        assertEquals(BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, residents.get(0).scheduleWindowSeed());
        assertEquals(BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, residents.get(0).runtimeRoleState());
        assertEquals("projected_local_labor", residents.get(0).roleProfile().profileId());
        assertEquals("labor", residents.get(0).roleProfile().goalDomainId());
        assertEquals(BannerModSettlementResidentSchedulePolicySeed.LOCAL_LABOR_DAY, residents.get(0).schedulePolicy().policySeed());
        assertEquals(BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, residents.get(0).schedulePolicy().scheduleWindowSeed());
        assertEquals(BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE, residents.get(0).serviceContract().actorState());
        assertEquals(localBuildingUuid, residents.get(0).serviceContract().serviceBuildingUuid());
        assertEquals("bannermod:crop_area", residents.get(0).serviceContract().serviceBuildingTypeId());
        assertEquals(BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, residents.get(0).jobDefinition().handlerSeed());
        assertEquals(BannerModSettlementBuildingCategory.FOOD, residents.get(0).jobDefinition().targetBuildingCategory());
        assertEquals(BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, residents.get(0).jobDefinition().targetBuildingProfileSeed());
        assertEquals(BannerModSettlementJobTargetSelectionMode.SERVICE_BUILDING, residents.get(0).jobTargetSelectionState().selectionMode());
        assertEquals(BannerModSettlementResidentAssignmentState.UNASSIGNED, residents.get(1).assignmentState());
        assertEquals(BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, residents.get(1).scheduleWindowSeed());
        assertEquals(BannerModSettlementResidentRuntimeRoleState.FLOATING_LABOR, residents.get(1).runtimeRoleState());
        assertEquals("projected_floating_labor", residents.get(1).roleProfile().profileId());
        assertEquals(BannerModSettlementResidentSchedulePolicySeed.FLOATING_LABOR_FLEX, residents.get(1).schedulePolicy().policySeed());
        assertEquals(BannerModSettlementServiceActorState.FLOATING_SERVICE, residents.get(1).serviceContract().actorState());
        assertEquals(BannerModSettlementJobHandlerSeed.FLOATING_LABOR_POOL, residents.get(1).jobDefinition().handlerSeed());
        assertEquals(BannerModSettlementJobTargetSelectionMode.FLOATING_LABOR_POOL, residents.get(1).jobTargetSelectionState().selectionMode());
        assertEquals(BannerModSettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING, residents.get(2).assignmentState());
        assertEquals(BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, residents.get(2).scheduleWindowSeed());
        assertEquals(BannerModSettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT, residents.get(2).runtimeRoleState());
        assertEquals("orphaned_labor_assignment", residents.get(2).roleProfile().profileId());
        assertEquals(BannerModSettlementResidentSchedulePolicySeed.ORPHANED_LABOR_DAY, residents.get(2).schedulePolicy().policySeed());
        assertEquals(BannerModSettlementServiceActorState.ORPHANED_SERVICE, residents.get(2).serviceContract().actorState());
        assertEquals(BannerModSettlementJobHandlerSeed.ORPHANED_LABOR_RECOVERY, residents.get(2).jobDefinition().handlerSeed());
        assertEquals(residents.get(2).boundWorkAreaUuid(), residents.get(2).jobDefinition().targetBuildingUuid());
        assertEquals(BannerModSettlementJobTargetSelectionMode.ORPHANED_SERVICE_BUILDING, residents.get(2).jobTargetSelectionState().selectionMode());
        assertEquals(BannerModSettlementResidentAssignmentState.NOT_APPLICABLE, residents.get(3).assignmentState());
        assertEquals(BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, residents.get(3).scheduleWindowSeed());
        assertEquals(BannerModSettlementResidentRuntimeRoleState.VILLAGE_LIFE, residents.get(3).runtimeRoleState());
        assertEquals("village_life", residents.get(3).roleProfile().profileId());
        assertEquals(BannerModSettlementResidentSchedulePolicySeed.VILLAGE_LIFE_FLEX, residents.get(3).schedulePolicy().policySeed());
        assertEquals(BannerModSettlementServiceActorState.NOT_SERVICE_ACTOR, residents.get(3).serviceContract().actorState());
        assertEquals(BannerModSettlementJobHandlerSeed.VILLAGE_LIFE, residents.get(3).jobDefinition().handlerSeed());
        assertEquals(BannerModSettlementJobTargetSelectionMode.NONE, residents.get(3).jobTargetSelectionState().selectionMode());
        assertEquals(1, buildings.get(0).assignedWorkerCount());
        assertEquals(List.of(assignedWorkerUuid), buildings.get(0).assignedResidentUuids());
        assertEquals(BannerModSettlementBuildingCategory.FOOD, buildings.get(0).buildingCategory());
        assertEquals(BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, buildings.get(0).buildingProfileSeed());
    }

    @Test
    void summarizesAuthoredStockpileSeedsFromBuildingRecords() {
        BannerModSettlementStockpileSummary summary = BannerModSettlementService.summarizeStockpiles(List.of(
                new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 2, 54, true, false, List.of("farmers", "merchants")),
                new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:storage_area", new BlockPos(10, 64, 10), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 1, 27, false, true, List.of("farmers")),
                new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:crop_area", new BlockPos(20, 64, 20), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 99, 99, true, true, List.of("ignored"))
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
        BannerModSettlementBuildingRecord houseRecord = BannerModSettlementService.fromValidatedBuildingFields(UUID.randomUUID(), BuildingType.HOUSE, new BlockPos(0, 64, 0), 3, ownerUuid);
        BannerModSettlementBuildingRecord storageRecord = BannerModSettlementService.fromValidatedBuildingFields(UUID.randomUUID(), BuildingType.STORAGE, new BlockPos(8, 64, 0), 2, ownerUuid);
        BannerModSettlementBuildingRecord farmRecord = BannerModSettlementService.fromValidatedBuildingFields(UUID.randomUUID(), BuildingType.FARM, new BlockPos(16, 64, 0), 1, ownerUuid);
        BannerModSettlementStockpileSummary summary = BannerModSettlementService.summarizeStockpiles(List.of(storageRecord));

        assertEquals("bannermod:validated_house", houseRecord.buildingTypeId());
        assertEquals(3, houseRecord.residentCapacity());
        assertEquals(ownerUuid, houseRecord.ownerUuid());
        assertEquals("bannermod:validated_storage", storageRecord.buildingTypeId());
        assertEquals(2, summary.containerCount());
        assertEquals(54, summary.slotCapacity());
        assertEquals(BannerModSettlementBuildingProfileSeed.STORAGE, storageRecord.buildingProfileSeed());
        assertEquals("bannermod:validated_farm", farmRecord.buildingTypeId());
        assertEquals(1, farmRecord.workplaceSlots());
        assertEquals(BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, farmRecord.buildingProfileSeed());
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

        assertTrue(BannerModSettlementService.validatedBuildingBelongsToSettlement(settlement, matchingRecord));
        assertEquals(false, BannerModSettlementService.validatedBuildingBelongsToSettlement(settlement, wrongRecord));
    }

    @Test
    void mergesValidatedCapacityIntoLiveWorkAreaRecordWithoutBreakingBindingUuid() {
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
        BannerModSettlementBuildingRecord liveRecord = new BannerModSettlementBuildingRecord(
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
        BannerModSettlementBuildingRecord expectedValidated = BannerModSettlementService.fromValidatedBuildingFields(
                liveWorkAreaUuid,
                BuildingType.FARM,
                origin,
                4,
                ownerUuid
        );

        BannerModSettlementBuildingRecord merged = BannerModSettlementService.mergeValidatedBuildingIntoLiveRecord(record, liveRecord);

        assertEquals(liveWorkAreaUuid, merged.buildingUuid());
        assertEquals("bannermod:crop_area", merged.buildingTypeId());
        assertEquals(expectedValidated.workplaceSlots(), merged.workplaceSlots());
        assertEquals(expectedValidated.buildingCategory(), merged.buildingCategory());
        assertEquals(expectedValidated.buildingProfileSeed(), merged.buildingProfileSeed());
        assertEquals(ownerUuid, merged.ownerUuid());
        assertEquals(liveRecord.teamId(), merged.teamId());
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
        BannerModSettlementBuildingRecord building = BannerModSettlementService.fromValidatedBuilding(reloaded, null);

        assertEquals(0, building.assignedWorkerCount());
        assertEquals(List.of(), building.assignedResidentUuids());
    }

    @Test
    void summarizesMarketStateIntoAggregateSeed() {
        List<BannerModSettlementMarketRecord> markets = List.of(
                new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9),
                new BannerModSettlementMarketRecord(UUID.randomUUID(), "East Gate", false, 18, 4)
        );

        BannerModSettlementMarketState marketState = BannerModSettlementService.summarizeMarketState(markets);

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
                BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY,
                BannerModSettlementResidentScheduleWindowSeed.defaultFor(
                        BannerModSettlementResidentScheduleSeed.GOVERNING,
                        BannerModSettlementResidentRuntimeRoleState.GOVERNANCE
                )
        );
        assertEquals(
                BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY,
                BannerModSettlementResidentScheduleWindowSeed.defaultFor(
                        BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                        BannerModSettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT
                )
        );
        assertEquals(
                BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                BannerModSettlementResidentScheduleWindowSeed.defaultFor(
                        BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                        BannerModSettlementResidentRuntimeRoleState.VILLAGE_LIFE
                )
        );
    }

    @Test
    void schedulePolicyDefaultsFromResidentSeeds() {
        BannerModSettlementResidentRoleProfile floatingProfile = BannerModSettlementResidentRoleProfile.defaultFor(
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentRuntimeRoleState.FLOATING_LABOR,
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                BannerModSettlementResidentAssignmentState.UNASSIGNED
        );

        assertEquals(
                BannerModSettlementResidentSchedulePolicySeed.GOVERNANCE_CIVIC,
                BannerModSettlementResidentSchedulePolicy.defaultFor(
                        BannerModSettlementResidentScheduleSeed.GOVERNING,
                        BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY,
                        BannerModSettlementResidentRuntimeRoleState.GOVERNANCE,
                        BannerModSettlementResidentRoleProfile.defaultFor(
                                BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                                BannerModSettlementResidentRuntimeRoleState.GOVERNANCE,
                                BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                                BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
                        )
                ).policySeed()
        );
        assertEquals(
                BannerModSettlementResidentSchedulePolicySeed.FLOATING_LABOR_FLEX,
                BannerModSettlementResidentSchedulePolicy.defaultFor(
                        BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                        BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                        BannerModSettlementResidentRuntimeRoleState.FLOATING_LABOR,
                        floatingProfile
                ).policySeed()
        );
        assertEquals(
                BannerModSettlementResidentSchedulePolicySeed.ORPHANED_LABOR_DAY,
                BannerModSettlementResidentSchedulePolicy.defaultFor(
                        BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                        BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY,
                        BannerModSettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT,
                        BannerModSettlementResidentRoleProfile.defaultFor(
                                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                                BannerModSettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT,
                                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                                BannerModSettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING
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

        BannerModSettlementMarketState marketState = BannerModSettlementService.applySellerDispatchSeed(
                BannerModSettlementService.summarizeMarketState(List.of(
                        new BannerModSettlementMarketRecord(openMarketUuid, "Harbor Square", true, 27, 9),
                        new BannerModSettlementMarketRecord(closedMarketUuid, "East Gate", false, 18, 4)
                )),
                List.of(
                        new BannerModSettlementResidentRecord(readySellerUuid, BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, openMarketUuid, "bannermod:market_area"), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", openMarketUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new BannerModSettlementResidentRecord(blockedSellerUuid, BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, closedMarketUuid, "bannermod:market_area"), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", closedMarketUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, cropAreaUuid, "bannermod:crop_area"), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", cropAreaUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE, BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, BannerModSettlementResidentRuntimeRoleState.FLOATING_LABOR, BannerModSettlementResidentServiceContract.notServiceActor(), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", null, BannerModSettlementResidentAssignmentState.UNASSIGNED)
                ),
                List.of(
                        new BannerModSettlementBuildingRecord(openMarketUuid, "bannermod:market_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(readySellerUuid), false, 0, 0, false, false, List.of()),
                        new BannerModSettlementBuildingRecord(closedMarketUuid, "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(blockedSellerUuid), false, 0, 0, false, false, List.of()),
                        new BannerModSettlementBuildingRecord(cropAreaUuid, "bannermod:crop_area", new BlockPos(16, 64, 16), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of())
                )
        );

        assertEquals(2, marketState.sellerDispatchCount());
        assertEquals(1, marketState.readySellerDispatchCount());
        assertEquals(List.of(
                new BannerModSettlementSellerDispatchRecord(readySellerUuid, openMarketUuid, "Harbor Square", BannerModSettlementSellerDispatchState.READY),
                new BannerModSettlementSellerDispatchRecord(blockedSellerUuid, closedMarketUuid, "East Gate", BannerModSettlementSellerDispatchState.MARKET_CLOSED)
        ), marketState.sellerDispatches());

        List<BannerModSettlementResidentRecord> residents = BannerModSettlementService.applyResidentJobTargetSelectionStates(
                List.of(
                        new BannerModSettlementResidentRecord(readySellerUuid, BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, openMarketUuid, "bannermod:market_area"), new BannerModSettlementResidentJobDefinition(BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, openMarketUuid, "bannermod:market_area", BannerModSettlementBuildingCategory.MARKET, BannerModSettlementBuildingProfileSeed.MARKET), BannerModSettlementResidentJobTargetSelectionState.none(), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", openMarketUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, BannerModSettlementResidentRoleProfile.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING)),
                        new BannerModSettlementResidentRecord(blockedSellerUuid, BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, closedMarketUuid, "bannermod:market_area"), new BannerModSettlementResidentJobDefinition(BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, closedMarketUuid, "bannermod:market_area", BannerModSettlementBuildingCategory.MARKET, BannerModSettlementBuildingProfileSeed.MARKET), BannerModSettlementResidentJobTargetSelectionState.none(), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", closedMarketUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, BannerModSettlementResidentRoleProfile.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING)),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, cropAreaUuid, "bannermod:crop_area"), new BannerModSettlementResidentJobDefinition(BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, cropAreaUuid, "bannermod:crop_area", BannerModSettlementBuildingCategory.FOOD, BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION), BannerModSettlementResidentJobTargetSelectionState.none(), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", cropAreaUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, BannerModSettlementResidentRoleProfile.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING))
                ),
                marketState
        );

        assertEquals(BannerModSettlementJobTargetSelectionMode.SELLER_MARKET_DISPATCH, residents.get(0).jobTargetSelectionState().selectionMode());
        assertEquals(openMarketUuid, residents.get(0).jobTargetSelectionState().targetMarketUuid());
        assertEquals("Harbor Square", residents.get(0).jobTargetSelectionState().targetMarketName());
        assertEquals(BannerModSettlementJobTargetSelectionMode.SELLER_MARKET_CLOSED, residents.get(1).jobTargetSelectionState().selectionMode());
        assertEquals(closedMarketUuid, residents.get(1).jobTargetSelectionState().targetMarketUuid());
        assertEquals("East Gate", residents.get(1).jobTargetSelectionState().targetMarketName());
        assertEquals(BannerModSettlementJobTargetSelectionMode.SERVICE_BUILDING, residents.get(2).jobTargetSelectionState().selectionMode());
    }

    @Test
    void summarizesDesiredGoodsFromBuildingProfilesStockpileTypesAndMarkets() {
        List<BannerModSettlementBuildingRecord> buildings = List.of(
                new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:crop_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of()),
                new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:mining_area", new BlockPos(10, 64, 10), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of()),
                new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:build_area", new BlockPos(20, 64, 20), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of()),
                new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:market_area", new BlockPos(30, 64, 30), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())
        );

        BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot = BannerModSettlementService.summarizeDesiredGoods(
                buildings,
                new BannerModSettlementStockpileSummary(1, 2, 54, 1, 0, List.of("farmers", "merchants")),
                new BannerModSettlementMarketState(2, 1, 45, 13, 0, 0, List.of(
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9),
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "East Gate", false, 18, 4)
                ), List.of())
        );

        assertEquals(List.of(
                new BannerModSettlementDesiredGoodSnapshot("food", 1),
                new BannerModSettlementDesiredGoodSnapshot("materials", 1),
                new BannerModSettlementDesiredGoodSnapshot("construction_materials", 1),
                new BannerModSettlementDesiredGoodSnapshot("market_goods", 3),
                new BannerModSettlementDesiredGoodSnapshot("storage_type:farmers", 1),
                new BannerModSettlementDesiredGoodSnapshot("storage_type:merchants", 1),
                new BannerModSettlementDesiredGoodSnapshot("trade_stock", 1)
        ), desiredGoodsSnapshot.desiredGoods());
    }

    @Test
    void summarizesTradeRouteHandoffSnapshotFromDispatchDemandAndRouteHints() {
        BannerModSettlementMarketState marketState = new BannerModSettlementMarketState(
                2,
                1,
                45,
                13,
                2,
                1,
                List.of(
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9),
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "East Gate", false, 18, 4)
                ),
                List.of(
                        new BannerModSettlementSellerDispatchRecord(UUID.randomUUID(), UUID.randomUUID(), "Harbor Square", BannerModSettlementSellerDispatchState.READY),
                        new BannerModSettlementSellerDispatchRecord(UUID.randomUUID(), UUID.randomUUID(), "East Gate", BannerModSettlementSellerDispatchState.MARKET_CLOSED)
                )
        );

        BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("market_goods", 3),
                new BannerModSettlementDesiredGoodSnapshot("trade_stock", 1),
                new BannerModSettlementDesiredGoodSnapshot("storage_type:merchants", 1)
        ));

        BannerModSettlementTradeRouteHandoffSnapshot handoffSnapshot = BannerModSettlementService.summarizeTradeRouteHandoffSnapshot(
                new BannerModSettlementStockpileSummary(2, 3, 81, 1, 1, List.of("farmers", "merchants")),
                marketState,
                desiredGoodsSnapshot,
                new BannerModSettlementService.ReservationSignalSeed(2, 24, Map.of("trade_stock", 24))
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

        BannerModSettlementSupplySignalState supplySignalState = BannerModSettlementService.summarizeSupplySignals(
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("food", 2),
                        new BannerModSettlementDesiredGoodSnapshot("materials", 1),
                        new BannerModSettlementDesiredGoodSnapshot("construction_materials", 1),
                        new BannerModSettlementDesiredGoodSnapshot("market_goods", 3),
                        new BannerModSettlementDesiredGoodSnapshot("storage_type:farmers", 1),
                        new BannerModSettlementDesiredGoodSnapshot("trade_stock", 1)
                )),
                new BannerModSettlementStockpileSummary(1, 2, 54, 1, 1, List.of("farmers")),
                new BannerModSettlementMarketState(
                        1,
                        1,
                        27,
                        9,
                        2,
                        1,
                        List.of(new BannerModSettlementMarketRecord(marketUuid, "Harbor Square", true, 27, 9)),
                        List.of(
                                new BannerModSettlementSellerDispatchRecord(UUID.randomUUID(), marketUuid, "Harbor Square", BannerModSettlementSellerDispatchState.READY),
                                new BannerModSettlementSellerDispatchRecord(UUID.randomUUID(), marketUuid, "Harbor Square", BannerModSettlementSellerDispatchState.MARKET_CLOSED)
                        )
                ),
                List.of(
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, cropAreaUuid, "bannermod:crop_area"), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", cropAreaUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, mineUuid, "bannermod:mining_area"), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", mineUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        new BannerModSettlementResidentRecord(UUID.randomUUID(), BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK, BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY, BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR, BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, marketUuid, "bannermod:market_area"), BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", marketUuid, BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING)
                ),
                List.of(
                        new BannerModSettlementBuildingRecord(cropAreaUuid, "bannermod:crop_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of()),
                        new BannerModSettlementBuildingRecord(mineUuid, "bannermod:mining_area", new BlockPos(10, 64, 10), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of()),
                        new BannerModSettlementBuildingRecord(marketUuid, "bannermod:market_area", new BlockPos(20, 64, 20), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of())
                ),
                BannerModSettlementService.ReservationSignalSeed.empty()
        );

        assertEquals(6, supplySignalState.signalCount());
        assertEquals(3, supplySignalState.shortageSignalCount());
        assertEquals(3, supplySignalState.shortageUnitCount());
        assertEquals(0, supplySignalState.reservationHintUnitCount());
        assertEquals(List.of(
                new BannerModSettlementSupplySignal("food", 2, 1, 1, 0),
                new BannerModSettlementSupplySignal("materials", 1, 1, 0, 0),
                new BannerModSettlementSupplySignal("construction_materials", 1, 0, 1, 0),
                new BannerModSettlementSupplySignal("market_goods", 3, 2, 1, 0),
                new BannerModSettlementSupplySignal("storage_type:farmers", 1, 1, 0, 0),
                new BannerModSettlementSupplySignal("trade_stock", 1, 2, 0, 0)
        ), supplySignalState.signals());
    }

    @Test
    void supplySignalsUseOnlySpecificReservationHints() {
        BannerModSettlementSupplySignalState supplySignalState = BannerModSettlementService.summarizeSupplySignals(
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("market_goods", 3),
                        new BannerModSettlementDesiredGoodSnapshot("food", 2)
                )),
                BannerModSettlementStockpileSummary.empty(),
                BannerModSettlementMarketState.empty(),
                List.of(),
                List.of(),
                new BannerModSettlementService.ReservationSignalSeed(1, 12, Map.of("market_goods", 12))
        );

        assertEquals(12, supplySignalState.reservationHintUnitCount());
        assertEquals(new BannerModSettlementSupplySignal("market_goods", 3, 0, 3, 12), supplySignalState.signals().get(0));
        assertEquals(new BannerModSettlementSupplySignal("food", 2, 0, 2, 0), supplySignalState.signals().get(1));
    }

    @Test
    void summarizesReservationSignalSeedAndFeedsTradeAndMerchantHints() {
        UUID farmerStorageUuid = UUID.randomUUID();
        UUID merchantPortUuid = UUID.randomUUID();
        BannerModSettlementService.ReservationSignalSeed reservationSignalSeed = BannerModSettlementService.summarizeReservationSignalSeed(
                List.of(
                        new BannerModSettlementBuildingRecord(farmerStorageUuid, "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 1, 27, true, false, List.of("farmers")),
                        new BannerModSettlementBuildingRecord(merchantPortUuid, "bannermod:storage_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 1, 27, true, true, List.of("merchants"))
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
        BannerModSettlementProjectCandidateSnapshot storageCandidate = BannerModSettlementService.summarizeProjectCandidate(
                List.of(
                        new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:crop_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of()),
                        new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())
                ),
                BannerModSettlementStockpileSummary.empty(),
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("food", 1),
                        new BannerModSettlementDesiredGoodSnapshot("market_goods", 2)
                )),
                new BannerModSettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                true,
                true
        );

        assertEquals("storage_foundation", storageCandidate.candidateId());
        assertEquals(BannerModSettlementBuildingProfileSeed.STORAGE, storageCandidate.targetBuildingProfileSeed());
        assertEquals(5, storageCandidate.priority());
        assertEquals(true, storageCandidate.governedSettlement());
        assertEquals(true, storageCandidate.claimedSettlement());
        assertEquals(List.of("storage_missing", "goods_pressure", "market_access_present"), storageCandidate.driverIds());

        BannerModSettlementProjectCandidateSnapshot foodCandidate = BannerModSettlementService.summarizeProjectCandidate(
                List.of(
                        new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 2, 54, true, false, List.of("farmers")),
                        new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())
                ),
                new BannerModSettlementStockpileSummary(1, 2, 54, 1, 0, List.of("farmers")),
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("food", 2),
                        new BannerModSettlementDesiredGoodSnapshot("market_goods", 1)
                )),
                new BannerModSettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                false,
                true
        );

        assertEquals("food_capacity_growth", foodCandidate.candidateId());
        assertEquals(BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, foodCandidate.targetBuildingProfileSeed());
        assertEquals(3, foodCandidate.priority());
        assertEquals(List.of("food_demand", "storage_type:farmers"), foodCandidate.driverIds());
    }

    @Test
    void logisticsDerivationServiceCombinesStockpileProjectAndSupplySeeds() {
        UUID storageUuid = UUID.randomUUID();
        UUID marketUuid = UUID.randomUUID();
        BannerModSettlementBuildingRecord storage = new BannerModSettlementBuildingRecord(storageUuid, "bannermod:storage_area", new BlockPos(0, 64, 0), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), true, 2, 54, true, true, List.of("merchants"));
        BannerModSettlementBuildingRecord market = new BannerModSettlementBuildingRecord(marketUuid, "bannermod:market_area", new BlockPos(8, 64, 8), UUID.randomUUID(), "blueguild", 0, 1, 1, List.of(UUID.randomUUID()), false, 0, 0, false, false, List.of());
        BannerModSettlementResidentRecord seller = new BannerModSettlementResidentRecord(
                UUID.randomUUID(),
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY,
                BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR,
                BannerModSettlementResidentServiceContract.defaultFor(
                        BannerModSettlementResidentRole.CONTROLLED_WORKER,
                        BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING,
                        marketUuid,
                        "bannermod:market_area"
                ),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                marketUuid,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
        BannerModSettlementMarketState marketState = new BannerModSettlementMarketState(
                1,
                1,
                27,
                9,
                1,
                1,
                List.of(new BannerModSettlementMarketRecord(marketUuid, "Harbor Square", true, 27, 9)),
                List.of(new BannerModSettlementSellerDispatchRecord(seller.residentUuid(), marketUuid, "Harbor Square", BannerModSettlementSellerDispatchState.READY))
        );

        BannerModSettlementLogisticsDerivationService.LogisticsResult logistics = BannerModSettlementLogisticsDerivationService.derive(
                List.of(storage, market),
                List.of(seller),
                marketState,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                true
        );

        BannerModSettlementStockpileSummary expectedStockpile = BannerModSettlementService.summarizeStockpiles(List.of(storage, market), List.of());
        BannerModSettlementDesiredGoodsSnapshot expectedDesiredGoods = BannerModSettlementService.summarizeDesiredGoods(
                List.of(storage, market),
                expectedStockpile,
                marketState,
                BannerModSeaTradeSummary.summarise(List.of())
        );
        BannerModSettlementProjectCandidateSnapshot expectedProject = BannerModSettlementService.summarizeProjectCandidate(
                List.of(storage, market),
                expectedStockpile,
                expectedDesiredGoods,
                marketState,
                true,
                true
        );
        BannerModSettlementTradeRouteHandoffSnapshot expectedTradeRouteHandoff = BannerModSettlementService.summarizeTradeRouteHandoffSnapshot(
                expectedStockpile,
                marketState,
                expectedDesiredGoods,
                BannerModSettlementService.ReservationSignalSeed.empty(),
                BannerModSeaTradeSummary.summarise(List.of()),
                List.of()
        );
        BannerModSettlementSupplySignalState expectedSupplySignals = BannerModSettlementService.summarizeSupplySignals(
                expectedDesiredGoods,
                expectedStockpile,
                marketState,
                List.of(seller),
                List.of(storage, market),
                BannerModSettlementService.ReservationSignalSeed.empty(),
                BannerModSeaTradeSummary.summarise(List.of())
        );

        assertEquals(expectedStockpile, logistics.stockpileSummary());
        assertEquals(expectedDesiredGoods, logistics.desiredGoodsSnapshot());
        assertEquals(expectedProject, logistics.projectCandidateSnapshot());
        assertEquals(expectedTradeRouteHandoff, logistics.tradeRouteHandoffSnapshot());
        assertEquals(expectedSupplySignals, logistics.supplySignalState());
    }

    @Test
    void summarizesDesiredGoodsIncludesSeaTradeImportAndExportDrivers() {
        BannerModSeaTradeSummary.Summary seaTradeSummary = new BannerModSeaTradeSummary.Summary(
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"), 4),
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"), 2),
                List.of()
        );

        BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot = BannerModSettlementService.summarizeDesiredGoods(
                List.of(),
                BannerModSettlementStockpileSummary.empty(),
                BannerModSettlementMarketState.empty(),
                seaTradeSummary
        );

        assertEquals(List.of(
                new BannerModSettlementDesiredGoodSnapshot("sea_import:minecraft:iron_ingot", 2),
                new BannerModSettlementDesiredGoodSnapshot("sea_export:minecraft:wheat", 4)
        ), desiredGoodsSnapshot.desiredGoods());
    }

    @Test
    void summarizesSupplySignalsCountsSeaTradeMarketAndStorageCoverage() {
        BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("storage_type:merchants", 1),
                new BannerModSettlementDesiredGoodSnapshot("market_goods", 2),
                new BannerModSettlementDesiredGoodSnapshot("trade_stock", 3),
                new BannerModSettlementDesiredGoodSnapshot("sea_import:minecraft:iron_ingot", 4),
                new BannerModSettlementDesiredGoodSnapshot("sea_export:minecraft:wheat", 5)
        ));
        BannerModSeaTradeSummary.Summary seaTradeSummary = new BannerModSeaTradeSummary.Summary(
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"), 5),
                Map.of(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"), 4),
                List.of()
        );

        BannerModSettlementSupplySignalState signals = BannerModSettlementService.summarizeSupplySignals(
                desiredGoodsSnapshot,
                new BannerModSettlementStockpileSummary(1, 1, 27, 0, 2, List.of("merchants")),
                new BannerModSettlementMarketState(1, 1, 27, 9, 2, 2, List.of(), List.of()),
                List.of(),
                List.of(),
                BannerModSettlementService.ReservationSignalSeed.empty(),
                seaTradeSummary
        );

        assertEquals(new BannerModSettlementSupplySignalState(
                5,
                0,
                0,
                0,
                List.of(
                        new BannerModSettlementSupplySignal("storage_type:merchants", 1, 1, 0, 0),
                        new BannerModSettlementSupplySignal("market_goods", 2, 2, 0, 0),
                        new BannerModSettlementSupplySignal("trade_stock", 3, 3, 0, 0),
                        new BannerModSettlementSupplySignal("sea_import:minecraft:iron_ingot", 4, 4, 0, 0),
                        new BannerModSettlementSupplySignal("sea_export:minecraft:wheat", 5, 5, 0, 0)
                )
        ), signals);
    }

    @Test
    void summarizesProjectCandidatePrefersMarketFoundationWhenDemandExistsWithoutMarket() {
        BannerModSettlementProjectCandidateSnapshot candidate = BannerModSettlementService.summarizeProjectCandidate(
                List.of(storageBuilding(false, false, List.of("merchants"))),
                new BannerModSettlementStockpileSummary(1, 1, 27, 0, 0, List.of("merchants")),
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("market_goods", 2)
                )),
                BannerModSettlementMarketState.empty(),
                true,
                false
        );

        assertEquals("market_foundation", candidate.candidateId());
        assertEquals(BannerModSettlementBuildingProfileSeed.MARKET, candidate.targetBuildingProfileSeed());
        assertEquals(4, candidate.priority());
        assertEquals(List.of("market_missing", "market_goods_demand", "stockpile_ready"), candidate.driverIds());
    }

    @Test
    void summarizesProjectCandidateRecoversClosedMarketsBeforeExpansion() {
        BannerModSettlementProjectCandidateSnapshot candidate = BannerModSettlementService.summarizeProjectCandidate(
                List.of(
                        storageBuilding(false, false, List.of()),
                        building("bannermod:market_area", BannerModSettlementBuildingProfileSeed.MARKET)
                ),
                new BannerModSettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                BannerModSettlementDesiredGoodsSnapshot.empty(),
                new BannerModSettlementMarketState(2, 1, 27, 9, 1, 1, List.of(
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9),
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "East Gate", false, 18, 4)
                ), List.of()),
                false,
                false
        );

        assertEquals("market_recovery", candidate.candidateId());
        assertEquals(BannerModSettlementBuildingProfileSeed.MARKET, candidate.targetBuildingProfileSeed());
        assertEquals(List.of("closed_market_capacity", "seller_ready"), candidate.driverIds());
    }

    @Test
    void summarizesProjectCandidateUsesMaterialPressureWhenStorageAndMarketsExist() {
        BannerModSettlementProjectCandidateSnapshot candidate = BannerModSettlementService.summarizeProjectCandidate(
                List.of(
                        storageBuilding(false, false, List.of()),
                        building("bannermod:market_area", BannerModSettlementBuildingProfileSeed.MARKET)
                ),
                new BannerModSettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("materials", 2)
                )),
                new BannerModSettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                false,
                true
        );

        assertEquals("material_capacity_growth", candidate.candidateId());
        assertEquals(BannerModSettlementBuildingProfileSeed.MATERIAL_PRODUCTION, candidate.targetBuildingProfileSeed());
        assertEquals(List.of("materials_demand"), candidate.driverIds());
    }

    @Test
    void summarizesProjectCandidateUsesConstructionPressureAndCanSettleOnNone() {
        BannerModSettlementProjectCandidateSnapshot constructionCandidate = BannerModSettlementService.summarizeProjectCandidate(
                List.of(
                        storageBuilding(false, false, List.of()),
                        building("bannermod:market_area", BannerModSettlementBuildingProfileSeed.MARKET)
                ),
                new BannerModSettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("construction_materials", 1)
                )),
                new BannerModSettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                false,
                false
        );
        BannerModSettlementProjectCandidateSnapshot noneCandidate = BannerModSettlementService.summarizeProjectCandidate(
                List.of(
                        storageBuilding(false, false, List.of()),
                        building("bannermod:market_area", BannerModSettlementBuildingProfileSeed.MARKET),
                        building("bannermod:crop_area", BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION)
                ),
                new BannerModSettlementStockpileSummary(1, 1, 27, 0, 0, List.of()),
                new BannerModSettlementDesiredGoodsSnapshot(List.of(
                        new BannerModSettlementDesiredGoodSnapshot("food", 1)
                )),
                new BannerModSettlementMarketState(1, 1, 27, 9, 0, 0, List.of(
                        new BannerModSettlementMarketRecord(UUID.randomUUID(), "Harbor Square", true, 27, 9)
                ), List.of()),
                false,
                false
        );

        assertEquals("construction_capacity_growth", constructionCandidate.candidateId());
        assertEquals(BannerModSettlementBuildingProfileSeed.CONSTRUCTION, constructionCandidate.targetBuildingProfileSeed());
        assertEquals("none", noneCandidate.candidateId());
        assertEquals(0, noneCandidate.priority());
    }

    private static BannerModSettlementBuildingRecord building(String typeId,
                                                              BannerModSettlementBuildingProfileSeed profileSeed) {
        return new BannerModSettlementBuildingRecord(
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

    private static BannerModSettlementBuildingRecord storageBuilding(boolean routed,
                                                                     boolean portEntrypoint,
                                                                     List<String> typeIds) {
        return new BannerModSettlementBuildingRecord(
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
                BannerModSettlementBuildingProfileSeed.STORAGE.category(),
                BannerModSettlementBuildingProfileSeed.STORAGE
        );
    }
}
