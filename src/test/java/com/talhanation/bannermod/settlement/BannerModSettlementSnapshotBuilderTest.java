package com.talhanation.bannermod.settlement;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BannerModSettlementSnapshotBuilderTest {

    @Test
    void summarizeCountsAggregatesCapacitiesAndWorkerAssignmentBuckets() {
        Object counts = summarizeCounts(
                List.of(
                        new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:house", BlockPos.ZERO, null, null, 3, 2, 1, List.of()),
                        new BannerModSettlementBuildingRecord(UUID.randomUUID(), "bannermod:mine", BlockPos.ZERO, null, null, -4, -2, -1, List.of())
                ),
                List.of(
                        worker(BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING),
                        worker(BannerModSettlementResidentAssignmentState.UNASSIGNED),
                        worker(BannerModSettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING),
                        worker(BannerModSettlementResidentAssignmentState.NOT_APPLICABLE),
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

    private static BannerModSettlementResidentRecord worker(BannerModSettlementResidentAssignmentState assignmentState) {
        return new BannerModSettlementResidentRecord(
                UUID.randomUUID(),
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentRuntimeRoleState.FLOATING_LABOR,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "blueguild",
                null,
                assignmentState
        );
    }

    private static BannerModSettlementResidentRecord villager() {
        return new BannerModSettlementResidentRecord(
                UUID.randomUUID(),
                BannerModSettlementResidentRole.VILLAGER,
                BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                BannerModSettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                "blueguild",
                null,
                BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static Object summarizeCounts(List<BannerModSettlementBuildingRecord> buildings,
                                          List<BannerModSettlementResidentRecord> residents) {
        try {
            Method method = BannerModSettlementSnapshotBuilder.class.getDeclaredMethod("summarizeCounts", List.class, List.class);
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
