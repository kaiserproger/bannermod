package com.talhanation.bannermod.settlement;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementSnapshotBuilderTest {

    @Test
    void summarizeCountsAggregatesCapacitiesAndWorkerAssignmentBuckets() {
        Object counts = summarizeCounts(
                List.of(
                        new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:house", BlockPos.ZERO, null, null, 3, 2, 1, List.of()),
                        new SettlementBuildingRecord(UUID.randomUUID(), "bannermod:mine", BlockPos.ZERO, null, null, -4, -2, -1, List.of())
                ),
                List.of(
                        worker(SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        worker(SettlementResidentAssignmentState.UNASSIGNED),
                        worker(SettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING),
                        worker(SettlementResidentAssignmentState.NOT_APPLICABLE),
                        villager()
                )
        );

        assertEquals(3, accessor(counts, "residentCapacity"));
        assertEquals(2, accessor(counts, "workplaceCapacity"));
        assertEquals(1, accessor(counts, "assignedWorkerCount"));
        assertEquals(1, accessor(counts, "assignedResidentCount"));
        assertEquals(1, accessor(counts, "unassignedWorkerCount"));
        assertEquals(1, accessor(counts, "missingWorkAreaAssignmentCount"));
    }

    @Test
    void summarizeCountsReturnsZerosForEmptyInputs() {
        Object counts = summarizeCounts(List.of(), List.of());

        assertEquals(0, accessor(counts, "residentCapacity"));
        assertEquals(0, accessor(counts, "workplaceCapacity"));
        assertEquals(0, accessor(counts, "assignedWorkerCount"));
        assertEquals(0, accessor(counts, "assignedResidentCount"));
        assertEquals(0, accessor(counts, "unassignedWorkerCount"));
        assertEquals(0, accessor(counts, "missingWorkAreaAssignmentCount"));
    }

    private static SettlementResidentRecord worker(SettlementResidentAssignmentState assignmentState) {
        return new SettlementResidentRecord(
                UUID.randomUUID(),
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentRuntimeRoleState.FLOATING_LABOR,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                null,
                assignmentState
        );
    }

    private static SettlementResidentRecord villager() {
        return new SettlementResidentRecord(
                UUID.randomUUID(),
                SettlementResidentRole.VILLAGER,
                SettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                SettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                "blueguild",
                null,
                SettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static Object summarizeCounts(List<SettlementBuildingRecord> buildings,
                                          List<SettlementResidentRecord> residents) {
        try {
            Method method = SettlementSnapshotBuilder.class.getDeclaredMethod("summarizeCounts", List.class, List.class);
            method.setAccessible(true);
            return method.invoke(null, buildings, residents);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static int accessor(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (int) method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
