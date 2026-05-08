package com.talhanation.bannermod.settlement.job;

import com.talhanation.bannermod.settlement.BannerModSettlementJobHandlerSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentMode;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRole;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRuntimeRoleState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentServiceContract;
import com.talhanation.bannermod.settlement.BannerModSettlementServiceActorState;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderStatus;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobHandlerRegistryTest {

    @Test
    void defaultsRegistryExposesBothBuiltInHandlers() {
        JobHandlerRegistry registry = JobHandlerRegistry.defaults();

        assertEquals(2, registry.size());
        assertTrue(registry.lookupById(HarvestJobHandler.ID).isPresent());
        assertTrue(registry.lookupById(BuildJobHandler.ID).isPresent());
        assertTrue(registry.all().stream().anyMatch(h -> h instanceof HarvestJobHandler));
        assertTrue(registry.all().stream().anyMatch(h -> h instanceof BuildJobHandler));
    }

    @Test
    void lookupBySeedReturnsHandlerWhoseHandlesMatches() {
        JobHandlerRegistry registry = JobHandlerRegistry.defaults();

        Optional<JobHandler> harvest = registry.lookup(BannerModSettlementJobHandlerSeed.FLOATING_LABOR_POOL);
        Optional<JobHandler> build = registry.lookup(BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR);

        assertTrue(harvest.isPresent());
        assertTrue(build.isPresent());
        assertEquals(BannerModSettlementJobHandlerSeed.FLOATING_LABOR_POOL, harvest.get().handles());
        assertEquals(BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR, build.get().handles());
    }

    @Test
    void registerCustomHandlerThenLookupByIdReturnsSameInstance() {
        JobHandlerRegistry registry = new JobHandlerRegistry();
        JobHandler custom = new TestJobHandler(
                ResourceLocation.fromNamespaceAndPath("bannermod", "custom_governance"),
                BannerModSettlementJobHandlerSeed.GOVERNANCE
        );

        registry.register(custom);

        assertSame(custom, registry.lookupById(ResourceLocation.fromNamespaceAndPath("bannermod", "custom_governance")).orElse(null));
        assertSame(custom, registry.lookup(BannerModSettlementJobHandlerSeed.GOVERNANCE).orElse(null));
    }

    @Test
    void lastRegistrationWinsForSameSeed() {
        JobHandlerRegistry registry = new JobHandlerRegistry();
        JobHandler first = new TestJobHandler(
                ResourceLocation.fromNamespaceAndPath("bannermod", "first"),
                BannerModSettlementJobHandlerSeed.VILLAGE_LIFE
        );
        JobHandler second = new TestJobHandler(
                ResourceLocation.fromNamespaceAndPath("bannermod", "second"),
                BannerModSettlementJobHandlerSeed.VILLAGE_LIFE
        );

        registry.register(first);
        registry.register(second);

        // Seed binding reflects the most recent registration.
        assertSame(second, registry.lookup(BannerModSettlementJobHandlerSeed.VILLAGE_LIFE).orElse(null));
        // Both remain reachable by their distinct ids; we do not evict the older handler from
        // the id index because its id was not reused.
        assertSame(first, registry.lookupById(ResourceLocation.fromNamespaceAndPath("bannermod", "first")).orElse(null));
        assertSame(second, registry.lookupById(ResourceLocation.fromNamespaceAndPath("bannermod", "second")).orElse(null));
        assertEquals(2, registry.size());
    }

    @Test
    void lookupForUnregisteredSeedReturnsEmpty() {
        JobHandlerRegistry registry = new JobHandlerRegistry();

        assertFalse(registry.lookup(BannerModSettlementJobHandlerSeed.ORPHANED_LABOR_RECOVERY).isPresent());
        assertFalse(registry.lookup(BannerModSettlementJobHandlerSeed.NONE).isPresent());
        assertFalse(registry.lookup(null).isPresent());
        assertFalse(registry.lookupById(null).isPresent());
        assertFalse(registry.lookupById(ResourceLocation.fromNamespaceAndPath("bannermod", "missing")).isPresent());
    }

    @Test
    void clearEmptiesRegistry() {
        JobHandlerRegistry registry = JobHandlerRegistry.defaults();
        assertTrue(registry.size() > 0);

        registry.clear();

        assertEquals(0, registry.size());
        assertTrue(registry.all().isEmpty());
        assertFalse(registry.lookup(BannerModSettlementJobHandlerSeed.FLOATING_LABOR_POOL).isPresent());
        assertFalse(registry.lookupById(HarvestJobHandler.ID).isPresent());
    }

    @Test
    void builtInHandlersAcceptProjectedControlledWorkerContext() {
        JobHandlerRegistry registry = JobHandlerRegistry.defaults();
        JobExecutionContext ctx = new JobExecutionContext(
                sampleProjectedWorker(),
                1000L,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        JobHandler harvest = registry.lookup(BannerModSettlementJobHandlerSeed.FLOATING_LABOR_POOL).orElseThrow();
        JobHandler build = registry.lookup(BannerModSettlementJobHandlerSeed.LOCAL_BUILDING_LABOR).orElseThrow();

        assertTrue(harvest.canHandle(ctx));
        assertTrue(build.canHandle(ctx));
        assertEquals(JobExecutionResult.COMPLETED, harvest.runOneStep(ctx));
        assertEquals(JobExecutionResult.COMPLETED, build.runOneStep(ctx));
    }

    @Test
    void builtInHandlersRejectNonProjectedContext() {
        JobHandler harvest = new HarvestJobHandler();
        JobExecutionContext ctx = new JobExecutionContext(
                sampleSettlementResident(),
                0L,
                null,
                null
        );

        assertFalse(harvest.canHandle(ctx));
    }

    @Test
    void harvestHandlerClaimsHaulResourceOrderForFloatingLaborResident() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        BannerModSettlementResidentRecord resident = sampleProjectedWorker();
        UUID claimUuid = UUID.randomUUID();
        UUID buildingUuid = resident.boundWorkAreaUuid();
        SettlementWorkOrder published = runtime.publish(SettlementWorkOrder.pendingTransport(
                claimUuid,
                buildingUuid,
                SettlementWorkOrderType.HAUL_RESOURCE,
                new BlockPos(5, 64, 5),
                new BlockPos(10, 64, 10),
                "minecraft:wheat",
                32,
                70,
                100L
        )).orElseThrow();
        JobExecutionContext ctx = new JobExecutionContext(
                resident, 200L, UUID.randomUUID(), buildingUuid, runtime);

        JobExecutionResult result = new HarvestJobHandler().runOneStep(ctx);

        assertEquals(JobExecutionResult.COMPLETED, result);
        SettlementWorkOrder afterClaim = runtime.find(published.orderUuid()).orElseThrow();
        assertEquals(SettlementWorkOrderStatus.CLAIMED, afterClaim.status());
        assertEquals(resident.residentUuid(), afterClaim.claimedByResidentUuid());
    }

    @Test
    void harvestHandlerClaimsFetchInputOrderForFloatingLaborResident() {
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        BannerModSettlementResidentRecord resident = sampleProjectedWorker();
        UUID claimUuid = UUID.randomUUID();
        UUID buildingUuid = resident.boundWorkAreaUuid();
        SettlementWorkOrder published = runtime.publish(SettlementWorkOrder.pendingTransport(
                claimUuid,
                buildingUuid,
                SettlementWorkOrderType.FETCH_INPUT,
                new BlockPos(2, 64, 2),
                new BlockPos(7, 64, 7),
                "minecraft:wheat_seeds",
                16,
                70,
                100L
        )).orElseThrow();
        JobExecutionContext ctx = new JobExecutionContext(
                resident, 200L, UUID.randomUUID(), buildingUuid, runtime);

        JobExecutionResult result = new HarvestJobHandler().runOneStep(ctx);

        assertEquals(JobExecutionResult.COMPLETED, result);
        SettlementWorkOrder afterClaim = runtime.find(published.orderUuid()).orElseThrow();
        assertEquals(SettlementWorkOrderStatus.CLAIMED, afterClaim.status());
        assertEquals(resident.residentUuid(), afterClaim.claimedByResidentUuid());
    }

    @Test
    void jobTaskDefinitionRejectsInvalidValues() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("bannermod", "t");
        BannerModSettlementJobHandlerSeed seed = BannerModSettlementJobHandlerSeed.FLOATING_LABOR_POOL;

        JobTaskDefinition ok = new JobTaskDefinition(id, seed, 0, 1, true, false);
        assertNotNull(ok);

        try {
            new JobTaskDefinition(id, seed, -1, 1, true, false);
            throw new AssertionError("expected IllegalArgumentException for negative tick cost");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            new JobTaskDefinition(id, seed, 0, 0, true, false);
            throw new AssertionError("expected IllegalArgumentException for zero concurrency");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static BannerModSettlementResidentRecord sampleProjectedWorker() {
        UUID residentUuid = UUID.randomUUID();
        UUID buildingUuid = UUID.randomUUID();
        return new BannerModSettlementResidentRecord(
                residentUuid,
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR,
                new BannerModSettlementResidentServiceContract(
                        BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                        buildingUuid,
                        "bannermod:crop_area"
                ),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.randomUUID(),
                "team",
                buildingUuid,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }

    private static BannerModSettlementResidentRecord sampleSettlementResident() {
        UUID residentUuid = UUID.randomUUID();
        return new BannerModSettlementResidentRecord(
                residentUuid,
                BannerModSettlementResidentRole.VILLAGER,
                BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                BannerModSettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                BannerModSettlementResidentServiceContract.defaultFor(
                        BannerModSettlementResidentRole.VILLAGER,
                        BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                        BannerModSettlementResidentAssignmentState.NOT_APPLICABLE,
                        null,
                        null
                ),
                BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private record TestJobHandler(ResourceLocation id, BannerModSettlementJobHandlerSeed seed) implements JobHandler {
        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public BannerModSettlementJobHandlerSeed handles() {
            return seed;
        }

        @Override
        public boolean canHandle(JobExecutionContext ctx) {
            return true;
        }

        @Override
        public JobExecutionResult runOneStep(JobExecutionContext ctx) {
            return JobExecutionResult.COMPLETED;
        }
    }
}
