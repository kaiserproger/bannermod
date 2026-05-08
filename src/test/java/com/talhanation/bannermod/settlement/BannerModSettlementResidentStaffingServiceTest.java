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

class BannerModSettlementResidentStaffingServiceTest {

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
}
