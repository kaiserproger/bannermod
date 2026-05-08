package com.talhanation.bannermod.settlement.workorder;

import com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentMode;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRole;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRuntimeRoleState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentServiceContract;
import com.talhanation.bannermod.settlement.BannerModSettlementServiceActorState;
import com.talhanation.bannermod.settlement.job.BuildJobHandler;
import com.talhanation.bannermod.settlement.job.HarvestJobHandler;
import com.talhanation.bannermod.settlement.job.JobExecutionContext;
import com.talhanation.bannermod.settlement.job.JobExecutionResult;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerClaimBehaviorTest {

    private static final UUID CLAIM = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID RESIDENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BUILDING = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void harvestHandlerClaimsMatchingFarmingOrderFromRuntime() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.HARVEST_CROP, new BlockPos(1, 64, 1), null, 80, 10L));
        BannerModSettlementResidentRecord resident = controlledResident();
        JobExecutionContext ctx = new JobExecutionContext(resident, 100L, RESIDENT, BUILDING, runtime);

        HarvestJobHandler handler = new HarvestJobHandler();

        assertTrue(handler.canHandle(ctx));
        JobExecutionResult result = handler.runOneStep(ctx);

        assertEquals(JobExecutionResult.COMPLETED, result);
        assertTrue(runtime.currentClaim(RESIDENT).isPresent());
        assertEquals(SettlementWorkOrderType.HARVEST_CROP, runtime.currentClaim(RESIDENT).orElseThrow().type());
    }

    @Test
    void harvestHandlerReturnsBlockedWhenNoOrderAvailable() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        BannerModSettlementResidentRecord resident = controlledResident();
        JobExecutionContext ctx = new JobExecutionContext(resident, 100L, RESIDENT, BUILDING, runtime);

        JobExecutionResult result = new HarvestJobHandler().runOneStep(ctx);

        assertEquals(JobExecutionResult.BLOCKED_NO_TARGET, result);
    }

    @Test
    void harvestHandlerKeepsExistingClaimAcrossSteps() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.HARVEST_CROP, new BlockPos(1, 64, 1), null, 80, 10L));
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.HARVEST_CROP, new BlockPos(1, 64, 2), null, 80, 12L));
        BannerModSettlementResidentRecord resident = controlledResident();
        JobExecutionContext ctx = new JobExecutionContext(resident, 100L, RESIDENT, BUILDING, runtime);

        HarvestJobHandler handler = new HarvestJobHandler();
        handler.runOneStep(ctx);
        UUID claimedFirst = runtime.currentClaim(RESIDENT).orElseThrow().orderUuid();

        JobExecutionResult second = handler.runOneStep(ctx);

        assertEquals(JobExecutionResult.COMPLETED, second);
        assertEquals(claimedFirst, runtime.currentClaim(RESIDENT).orElseThrow().orderUuid());
    }

    @Test
    void harvestHandlerRejectsConstructionOrder() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.BUILD_BLOCK, new BlockPos(1, 64, 1), null, 80, 10L));
        BannerModSettlementResidentRecord resident = controlledResident();
        JobExecutionContext ctx = new JobExecutionContext(resident, 100L, RESIDENT, BUILDING, runtime);

        JobExecutionResult result = new HarvestJobHandler().runOneStep(ctx);

        assertEquals(JobExecutionResult.BLOCKED_NO_TARGET, result);
        assertTrue(runtime.currentClaim(RESIDENT).isEmpty());
    }

    @Test
    void buildHandlerClaimsMatchingConstructionOrder() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.BUILD_BLOCK, new BlockPos(1, 64, 1), null, 70, 10L));
        BannerModSettlementResidentRecord resident = controlledResident();
        JobExecutionContext ctx = new JobExecutionContext(resident, 100L, RESIDENT, BUILDING, runtime);

        JobExecutionResult result = new BuildJobHandler().runOneStep(ctx);

        assertEquals(JobExecutionResult.COMPLETED, result);
        assertTrue(runtime.currentClaim(RESIDENT).isPresent());
    }

    @Test
    void buildHandlerClaimsAnimalOrderForAssignedPen() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.ANIMAL_BREED, new BlockPos(1, 64, 1), null, 90, 10L));
        BannerModSettlementResidentRecord resident = controlledResident("animal_pen_area");
        JobExecutionContext ctx = new JobExecutionContext(resident, 100L, RESIDENT, BUILDING, runtime);

        JobExecutionResult result = new BuildJobHandler().runOneStep(ctx);

        assertEquals(JobExecutionResult.COMPLETED, result);
        assertEquals(SettlementWorkOrderType.ANIMAL_BREED, runtime.currentClaim(RESIDENT).orElseThrow().type());
    }

    @Test
    void buildHandlerIgnoresFarmingOrder() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.HARVEST_CROP, new BlockPos(1, 64, 1), null, 80, 10L));
        BannerModSettlementResidentRecord resident = controlledResident();
        JobExecutionContext ctx = new JobExecutionContext(resident, 100L, RESIDENT, BUILDING, runtime);

        JobExecutionResult result = new BuildJobHandler().runOneStep(ctx);

        assertEquals(JobExecutionResult.BLOCKED_NO_TARGET, result);
        assertFalse(runtime.currentClaim(RESIDENT).isPresent());
    }

    private static BannerModSettlementResidentRecord controlledResident() {
        return controlledResident("crop_area");
    }

    private static BannerModSettlementResidentRecord controlledResident(String buildingTypeId) {
        BannerModSettlementResidentServiceContract serviceContract = new BannerModSettlementResidentServiceContract(
                BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                BUILDING,
                buildingTypeId
        );
        return new BannerModSettlementResidentRecord(
                RESIDENT,
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR,
                serviceContract,
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
                "teamA",
                BUILDING,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }
}
