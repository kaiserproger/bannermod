package com.talhanation.bannermod.settlement;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementResidentStaffingServiceTest {

    @Test
    void appliesResidentAssignmentSemanticsAndRollsAssignedWorkersIntoBuildings() {
        UUID localBuildingUuid = UUID.randomUUID();
        UUID assignedWorkerUuid = UUID.randomUUID();

        SettlementResidentStaffingService.StaffingResult staffing = SettlementResidentStaffingService.apply(
                List.of(
                        new SettlementResidentRecord(assignedWorkerUuid, SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.FLOATING_LABOR, SettlementResidentServiceContract.notServiceActor(), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", localBuildingUuid, SettlementResidentAssignmentState.UNASSIGNED),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.SETTLEMENT_IDLE, SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, SettlementResidentRuntimeRoleState.FLOATING_LABOR, SettlementResidentServiceContract.notServiceActor(), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", null, SettlementResidentAssignmentState.UNASSIGNED),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.CONTROLLED_WORKER, SettlementResidentScheduleSeed.ASSIGNED_WORK, SettlementResidentScheduleWindowSeed.LABOR_DAY, SettlementResidentRuntimeRoleState.FLOATING_LABOR, SettlementResidentServiceContract.notServiceActor(), SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, UUID.randomUUID(), "blueguild", UUID.randomUUID(), SettlementResidentAssignmentState.UNASSIGNED),
                        new SettlementResidentRecord(UUID.randomUUID(), SettlementResidentRole.VILLAGER, SettlementResidentScheduleSeed.SETTLEMENT_IDLE, SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, SettlementResidentRuntimeRoleState.VILLAGE_LIFE, SettlementResidentServiceContract.notServiceActor(), SettlementResidentMode.SETTLEMENT_RESIDENT, null, "blueguild", null, SettlementResidentAssignmentState.NOT_APPLICABLE)
                ),
                List.of(new SettlementBuildingRecord(localBuildingUuid, "bannermod:crop_area", new BlockPos(12, 64, 12), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())),
                SettlementMarketState.empty(),
                Set.of(localBuildingUuid)
        );
        List<SettlementResidentRecord> residents = staffing.residents();
        List<SettlementBuildingRecord> buildings = staffing.buildings();

        assertEquals(SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, residents.get(0).assignmentState());
        assertEquals(SettlementResidentScheduleWindowSeed.LABOR_DAY, residents.get(0).scheduleWindowSeed());
        assertEquals(SettlementResidentRuntimeRoleState.LOCAL_LABOR, residents.get(0).runtimeRoleState());
        assertEquals("projected_local_labor", residents.get(0).roleProfile().profileId());
        assertEquals("labor", residents.get(0).roleProfile().goalDomainId());
        assertEquals(SettlementResidentSchedulePolicySeed.LOCAL_LABOR_DAY, residents.get(0).schedulePolicy().policySeed());
        assertEquals(SettlementResidentScheduleWindowSeed.LABOR_DAY, residents.get(0).schedulePolicy().scheduleWindowSeed());
        assertEquals(SettlementServiceActorState.LOCAL_BUILDING_SERVICE, residents.get(0).serviceContract().actorState());
        assertEquals(localBuildingUuid, residents.get(0).serviceContract().serviceBuildingUuid());
        assertEquals("bannermod:crop_area", residents.get(0).serviceContract().serviceBuildingTypeId());
        assertEquals(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, residents.get(0).jobDefinition().handlerSeed());
        assertEquals(SettlementBuildingCategory.FOOD, residents.get(0).jobDefinition().targetBuildingCategory());
        assertEquals(SettlementBuildingProfileSeed.FOOD_PRODUCTION, residents.get(0).jobDefinition().targetBuildingProfileSeed());
        assertEquals(SettlementJobTargetSelectionMode.SERVICE_BUILDING, residents.get(0).jobTargetSelectionState().selectionMode());
        assertEquals(SettlementResidentAssignmentState.UNASSIGNED, residents.get(1).assignmentState());
        assertEquals(SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, residents.get(1).scheduleWindowSeed());
        assertEquals(SettlementResidentRuntimeRoleState.FLOATING_LABOR, residents.get(1).runtimeRoleState());
        assertEquals("projected_floating_labor", residents.get(1).roleProfile().profileId());
        assertEquals(SettlementResidentSchedulePolicySeed.FLOATING_LABOR_FLEX, residents.get(1).schedulePolicy().policySeed());
        assertEquals(SettlementServiceActorState.FLOATING_SERVICE, residents.get(1).serviceContract().actorState());
        assertEquals(SettlementJobHandlerSeed.FLOATING_LABOR_POOL, residents.get(1).jobDefinition().handlerSeed());
        assertEquals(SettlementJobTargetSelectionMode.FLOATING_LABOR_POOL, residents.get(1).jobTargetSelectionState().selectionMode());
        assertEquals(SettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING, residents.get(2).assignmentState());
        assertEquals(SettlementResidentScheduleWindowSeed.LABOR_DAY, residents.get(2).scheduleWindowSeed());
        assertEquals(SettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT, residents.get(2).runtimeRoleState());
        assertEquals("orphaned_labor_assignment", residents.get(2).roleProfile().profileId());
        assertEquals(SettlementResidentSchedulePolicySeed.ORPHANED_LABOR_DAY, residents.get(2).schedulePolicy().policySeed());
        assertEquals(SettlementServiceActorState.ORPHANED_SERVICE, residents.get(2).serviceContract().actorState());
        assertEquals(SettlementJobHandlerSeed.ORPHANED_LABOR_RECOVERY, residents.get(2).jobDefinition().handlerSeed());
        assertEquals(residents.get(2).boundWorkAreaUuid(), residents.get(2).jobDefinition().targetBuildingUuid());
        assertEquals(SettlementJobTargetSelectionMode.ORPHANED_SERVICE_BUILDING, residents.get(2).jobTargetSelectionState().selectionMode());
        assertEquals(SettlementResidentAssignmentState.NOT_APPLICABLE, residents.get(3).assignmentState());
        assertEquals(SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, residents.get(3).scheduleWindowSeed());
        assertEquals(SettlementResidentRuntimeRoleState.VILLAGE_LIFE, residents.get(3).runtimeRoleState());
        assertEquals("village_life", residents.get(3).roleProfile().profileId());
        assertEquals(SettlementResidentSchedulePolicySeed.VILLAGE_LIFE_FLEX, residents.get(3).schedulePolicy().policySeed());
        assertEquals(SettlementServiceActorState.NOT_SERVICE_ACTOR, residents.get(3).serviceContract().actorState());
        assertEquals(SettlementJobHandlerSeed.VILLAGE_LIFE, residents.get(3).jobDefinition().handlerSeed());
        assertEquals(SettlementJobTargetSelectionMode.NONE, residents.get(3).jobTargetSelectionState().selectionMode());
        assertEquals(1, buildings.get(0).assignedWorkerCount());
        assertEquals(List.of(assignedWorkerUuid), buildings.get(0).assignedResidentUuids());
        assertEquals(SettlementBuildingCategory.FOOD, buildings.get(0).buildingCategory());
        assertEquals(SettlementBuildingProfileSeed.FOOD_PRODUCTION, buildings.get(0).buildingProfileSeed());
    }

    @Test
    void derivedServiceBuildingBeatsLegacyBoundWorkAreaDuringStaffing() {
        UUID localBuildingUuid = UUID.randomUUID();
        UUID staleBoundUuid = UUID.randomUUID();
        UUID residentUuid = UUID.randomUUID();

        SettlementResidentRecord resident = new SettlementResidentRecord(
                residentUuid,
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                new SettlementResidentServiceContract(SettlementServiceActorState.LOCAL_BUILDING_SERVICE, localBuildingUuid, "bannermod:crop_area"),
                new SettlementResidentJobDefinition(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, localBuildingUuid, "bannermod:crop_area", SettlementBuildingCategory.FOOD, SettlementBuildingProfileSeed.FOOD_PRODUCTION),
                new SettlementResidentJobTargetSelectionState(SettlementJobTargetSelectionMode.SERVICE_BUILDING, null, null),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                staleBoundUuid,
                SettlementResidentAssignmentState.UNASSIGNED,
                SettlementResidentRoleProfile.defaultFor(
                        SettlementResidentRole.CONTROLLED_WORKER,
                        SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                        SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        SettlementResidentAssignmentState.UNASSIGNED
                )
        );

        SettlementResidentStaffingService.StaffingResult staffing = SettlementResidentStaffingService.apply(
                List.of(resident),
                List.of(new SettlementBuildingRecord(localBuildingUuid, "bannermod:crop_area", new BlockPos(16, 64, 16), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())),
                SettlementMarketState.empty(),
                Set.of(localBuildingUuid)
        );

        assertEquals(SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, staffing.residents().get(0).assignmentState());
        assertEquals(localBuildingUuid, staffing.residents().get(0).boundWorkAreaUuid());
        assertEquals(localBuildingUuid, staffing.residents().get(0).jobDefinition().targetBuildingUuid());
        assertEquals(1, staffing.buildings().get(0).assignedWorkerCount());
        assertEquals(List.of(residentUuid), staffing.buildings().get(0).assignedResidentUuids());
    }

    @Test
    void derivedServiceBuildingAlsoBeatsStaleJobDefinitionTarget() {
        UUID localBuildingUuid = UUID.randomUUID();
        UUID staleJobTargetUuid = UUID.randomUUID();
        SettlementResidentRecord resident = new SettlementResidentRecord(
                UUID.randomUUID(),
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                new SettlementResidentServiceContract(SettlementServiceActorState.LOCAL_BUILDING_SERVICE, localBuildingUuid, "bannermod:crop_area"),
                new SettlementResidentJobDefinition(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, staleJobTargetUuid, "bannermod:mine_area", SettlementBuildingCategory.MATERIAL, SettlementBuildingProfileSeed.MATERIAL_PRODUCTION),
                new SettlementResidentJobTargetSelectionState(SettlementJobTargetSelectionMode.SERVICE_BUILDING, null, null),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                UUID.randomUUID(),
                SettlementResidentAssignmentState.UNASSIGNED,
                SettlementResidentRoleProfile.defaultFor(
                        SettlementResidentRole.CONTROLLED_WORKER,
                        SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                        SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        SettlementResidentAssignmentState.UNASSIGNED
                )
        );

        SettlementResidentStaffingService.StaffingResult staffing = SettlementResidentStaffingService.apply(
                List.of(resident),
                List.of(new SettlementBuildingRecord(localBuildingUuid, "bannermod:crop_area", new BlockPos(20, 64, 20), UUID.randomUUID(), "blueguild", 0, 1, 0, List.of(), false, 0, 0, false, false, List.of())),
                SettlementMarketState.empty(),
                Set.of(localBuildingUuid)
        );

        assertEquals(localBuildingUuid, staffing.residents().get(0).boundWorkAreaUuid());
        assertEquals(localBuildingUuid, staffing.residents().get(0).effectiveWorkBuildingUuid());
        assertEquals(localBuildingUuid, staffing.residents().get(0).jobDefinition().targetBuildingUuid());
    }
}
