package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementResidentRecordTest {

    @Test
    void residentRecordRoundTripsScheduleSeed() {
        SettlementResidentRecord original = new SettlementResidentRecord(
                UUID.randomUUID(),
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                new SettlementResidentServiceContract(SettlementServiceActorState.LOCAL_BUILDING_SERVICE, UUID.randomUUID(), "bannermod:crop_area"),
                new SettlementResidentJobDefinition(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, UUID.randomUUID(), "bannermod:crop_area", SettlementBuildingCategory.FOOD, SettlementBuildingProfileSeed.FOOD_PRODUCTION),
                new SettlementResidentJobTargetSelectionState(SettlementJobTargetSelectionMode.SERVICE_BUILDING, null, null),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                UUID.randomUUID(),
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING,
                SettlementResidentRoleProfile.defaultFor(
                        SettlementResidentRole.CONTROLLED_WORKER,
                        SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                        SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                )
        );

        SettlementResidentRecord restored = SettlementResidentRecord.fromTag(original.toTag());

        assertEquals(original, restored);
    }

    @Test
    void residentRecordDefaultsLegacyScheduleSeedsWhenMissing() {
        UUID workerUuid = UUID.randomUUID();
        UUID workAreaUuid = UUID.randomUUID();
        CompoundTag workerTag = new CompoundTag();
        workerTag.putUUID("ResidentUuid", workerUuid);
        workerTag.putString("Role", SettlementResidentRole.CONTROLLED_WORKER.name());
        workerTag.putUUID("OwnerUuid", UUID.randomUUID());
        workerTag.putUUID("BoundWorkAreaUuid", workAreaUuid);

        SettlementResidentRecord worker = SettlementResidentRecord.fromTag(workerTag);

        assertEquals(SettlementResidentScheduleSeed.ASSIGNED_WORK, worker.scheduleSeed());
        assertEquals(SettlementResidentScheduleWindowSeed.LABOR_DAY, worker.scheduleWindowSeed());
        assertEquals(SettlementResidentRuntimeRoleState.LOCAL_LABOR, worker.runtimeRoleState());
        assertEquals("projected_local_labor", worker.roleProfile().profileId());
        assertEquals("labor", worker.roleProfile().goalDomainId());
        assertEquals(true, worker.roleProfile().prefersLocalBuilding());
        assertEquals(SettlementResidentSchedulePolicySeed.LOCAL_LABOR_DAY, worker.schedulePolicy().policySeed());
        assertEquals(SettlementResidentScheduleSeed.ASSIGNED_WORK, worker.schedulePolicy().scheduleSeed());
        assertEquals(SettlementResidentScheduleWindowSeed.LABOR_DAY, worker.schedulePolicy().scheduleWindowSeed());
        assertEquals("labor", worker.schedulePolicy().goalDomainId());
        assertEquals(true, worker.schedulePolicy().prefersLocalBuilding());
        assertEquals(SettlementServiceActorState.LOCAL_BUILDING_SERVICE, worker.serviceContract().actorState());
        assertEquals(workAreaUuid, worker.serviceContract().serviceBuildingUuid());
        assertEquals(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, worker.jobDefinition().handlerSeed());
        assertEquals(workAreaUuid, worker.jobDefinition().targetBuildingUuid());
        assertEquals(SettlementJobTargetSelectionMode.SERVICE_BUILDING, worker.jobTargetSelectionState().selectionMode());
        assertEquals(SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, worker.residentMode());
        assertEquals(SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING, worker.assignmentState());

        CompoundTag governorTag = new CompoundTag();
        governorTag.putUUID("ResidentUuid", UUID.randomUUID());
        governorTag.putString("Role", SettlementResidentRole.GOVERNOR_RECRUIT.name());

        SettlementResidentRecord governor = SettlementResidentRecord.fromTag(governorTag);

        assertEquals(SettlementResidentScheduleSeed.GOVERNING, governor.scheduleSeed());
        assertEquals(SettlementResidentScheduleWindowSeed.CIVIC_DAY, governor.scheduleWindowSeed());
        assertEquals(SettlementResidentRuntimeRoleState.GOVERNANCE, governor.runtimeRoleState());
        assertEquals("governance", governor.roleProfile().profileId());
        assertEquals("governance", governor.roleProfile().goalDomainId());
        assertEquals(SettlementResidentSchedulePolicySeed.GOVERNANCE_CIVIC, governor.schedulePolicy().policySeed());
        assertEquals(SettlementResidentScheduleWindowSeed.CIVIC_DAY, governor.schedulePolicy().scheduleWindowSeed());
        assertEquals(SettlementServiceActorState.NOT_SERVICE_ACTOR, governor.serviceContract().actorState());
        assertEquals(SettlementJobHandlerSeed.GOVERNANCE, governor.jobDefinition().handlerSeed());
        assertEquals(SettlementJobTargetSelectionMode.NONE, governor.jobTargetSelectionState().selectionMode());
        assertEquals(SettlementResidentMode.SETTLEMENT_RESIDENT, governor.residentMode());
        assertEquals(SettlementResidentAssignmentState.NOT_APPLICABLE, governor.assignmentState());

        CompoundTag unownedWorkerTag = new CompoundTag();
        unownedWorkerTag.putUUID("ResidentUuid", UUID.randomUUID());
        unownedWorkerTag.putString("Role", SettlementResidentRole.CONTROLLED_WORKER.name());

        SettlementResidentRecord unownedWorker = SettlementResidentRecord.fromTag(unownedWorkerTag);

        assertEquals(SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, unownedWorker.scheduleWindowSeed());
        assertEquals(SettlementResidentRuntimeRoleState.FLOATING_LABOR, unownedWorker.runtimeRoleState());
        assertEquals("projected_floating_labor", unownedWorker.roleProfile().profileId());
        assertEquals("labor", unownedWorker.roleProfile().goalDomainId());
        assertEquals(SettlementResidentSchedulePolicySeed.FLOATING_LABOR_FLEX, unownedWorker.schedulePolicy().policySeed());
        assertEquals(SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, unownedWorker.schedulePolicy().scheduleWindowSeed());
        assertEquals(SettlementServiceActorState.FLOATING_SERVICE, unownedWorker.serviceContract().actorState());
        assertEquals(SettlementJobHandlerSeed.FLOATING_LABOR_POOL, unownedWorker.jobDefinition().handlerSeed());
        assertEquals(SettlementJobTargetSelectionMode.FLOATING_LABOR_POOL, unownedWorker.jobTargetSelectionState().selectionMode());
        assertEquals(SettlementResidentMode.PROJECTED_CONTROLLED_WORKER, unownedWorker.residentMode());
        assertEquals(SettlementResidentAssignmentState.UNASSIGNED, unownedWorker.assignmentState());
    }

    @Test
    void residentRecordFallsBackForUnknownScheduleWindowSeed() {
        CompoundTag residentTag = new CompoundTag();
        residentTag.putUUID("ResidentUuid", UUID.randomUUID());
        residentTag.putString("Role", SettlementResidentRole.VILLAGER.name());
        residentTag.putString("ScheduleSeed", SettlementResidentScheduleSeed.SETTLEMENT_IDLE.name());
        residentTag.putString("RuntimeRoleSeed", SettlementResidentRuntimeRoleState.VILLAGE_LIFE.name());
        residentTag.putString("ScheduleWindowSeed", "NOT_A_REAL_WINDOW");

        SettlementResidentRecord resident = SettlementResidentRecord.fromTag(residentTag);

        assertEquals(SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX, resident.scheduleWindowSeed());
        assertEquals(SettlementResidentSchedulePolicySeed.VILLAGE_LIFE_FLEX, resident.schedulePolicy().policySeed());
    }

    @Test
    void effectiveWorkBuildingUuidPrefersDerivedServiceBindingOverLegacyTargets() {
        UUID serviceBuildingUuid = UUID.randomUUID();
        UUID targetBuildingUuid = UUID.randomUUID();
        UUID legacyBoundUuid = UUID.randomUUID();
        SettlementResidentRecord resident = new SettlementResidentRecord(
                UUID.randomUUID(),
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                new SettlementResidentServiceContract(SettlementServiceActorState.LOCAL_BUILDING_SERVICE, serviceBuildingUuid, "bannermod:crop_area"),
                new SettlementResidentJobDefinition(SettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, targetBuildingUuid, "bannermod:crop_area", SettlementBuildingCategory.FOOD, SettlementBuildingProfileSeed.FOOD_PRODUCTION),
                new SettlementResidentJobTargetSelectionState(SettlementJobTargetSelectionMode.SERVICE_BUILDING, null, null),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                legacyBoundUuid,
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING,
                SettlementResidentRoleProfile.defaultFor(
                        SettlementResidentRole.CONTROLLED_WORKER,
                        SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                        SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                        SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                )
        );

        assertEquals(serviceBuildingUuid, resident.effectiveWorkBuildingUuid());
    }

    @Test
    void residentRecordFallsBackForUnknownScheduleSeed() {
        UUID workAreaUuid = UUID.randomUUID();
        CompoundTag residentTag = new CompoundTag();
        residentTag.putUUID("ResidentUuid", UUID.randomUUID());
        residentTag.putString("Role", SettlementResidentRole.CONTROLLED_WORKER.name());
        residentTag.putUUID("BoundWorkAreaUuid", workAreaUuid);
        residentTag.putString("ScheduleSeed", "NOT_A_REAL_SCHEDULE");

        SettlementResidentRecord resident = SettlementResidentRecord.fromTag(residentTag);

        assertEquals(SettlementResidentScheduleSeed.ASSIGNED_WORK, resident.scheduleSeed());
        assertEquals(SettlementResidentSchedulePolicySeed.LOCAL_LABOR_DAY, resident.schedulePolicy().policySeed());
    }

    @Test
    void schedulePolicyFallsBackForUnknownScheduleSeed() {
        CompoundTag policyTag = new CompoundTag();
        policyTag.putString("ScheduleSeed", "NOT_A_REAL_SCHEDULE");

        SettlementResidentSchedulePolicy policy = SettlementResidentSchedulePolicy.fromTag(policyTag);

        assertEquals(SettlementResidentScheduleSeed.SETTLEMENT_IDLE, policy.scheduleSeed());
    }

    @Test
    void sellerDispatchRecordFallsBackForUnknownDispatchState() {
        CompoundTag dispatchTag = new CompoundTag();
        dispatchTag.putUUID("ResidentUuid", UUID.randomUUID());
        dispatchTag.putUUID("MarketUuid", UUID.randomUUID());
        dispatchTag.putString("DispatchState", "NOT_A_REAL_STATE");

        SettlementSellerDispatchRecord dispatch = SettlementSellerDispatchRecord.fromTag(dispatchTag);

        assertEquals(SettlementSellerDispatchState.READY, dispatch.dispatchState());
    }
}
