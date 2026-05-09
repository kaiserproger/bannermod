package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.SettlementResidentMode;
import com.talhanation.bannermod.settlement.SettlementResidentRecord;
import com.talhanation.bannermod.settlement.SettlementResidentRole;
import com.talhanation.bannermod.settlement.SettlementResidentRuntimeRoleState;
import com.talhanation.bannermod.settlement.SettlementResidentScheduleSeed;
import com.talhanation.bannermod.settlement.SettlementResidentServiceContract;
import com.talhanation.bannermod.settlement.SettlementSellerDispatchRecord;
import com.talhanation.bannermod.settlement.SettlementSellerDispatchState;
import com.talhanation.bannermod.settlement.SettlementServiceActorState;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime;
import com.talhanation.bannermod.settlement.dispatch.SellerResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.IdleResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SocialiseResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentRuntime;
import com.talhanation.bannermod.settlement.household.GoHomeResidentGoal;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModResidentGoalSchedulerTest {

    private static final long DAY_TICK_ACTIVE = 6000L;   // mid-day, in active window for LABOR_DAY
    private static final long DAY_TICK_NIGHT = 15000L;   // night, in rest window for all windows

    @Test
    void activePhaseLocalWorkerSelectsWorkGoalOverIdleFallback() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        SettlementResidentRecord worker = buildLocalWorker();
        ResidentGoalContext ctx = new ResidentGoalContext(worker, null, DAY_TICK_ACTIVE);

        scheduler.tick(ctx);

        Optional<ResidentTask> task = scheduler.currentTask(worker.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(WorkResidentGoal.ID, task.get().goalId());
    }

    @Test
    void nightTickSelectsRestOverIdle() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        SettlementResidentRecord resident = buildLocalWorker();
        ResidentGoalContext ctx = new ResidentGoalContext(resident, null, DAY_TICK_NIGHT);

        scheduler.tick(ctx);

        Optional<ResidentTask> task = scheduler.currentTask(resident.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(RestResidentGoal.ID, task.get().goalId());
    }

    @Test
    void unassignedVillagerInDaylightFlexSocialisesRatherThanWorks() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        SettlementResidentRecord resident = buildUnassignedVillager();
        ResidentGoalContext ctx = new ResidentGoalContext(resident, null, DAY_TICK_ACTIVE);

        scheduler.tick(ctx);

        Optional<ResidentTask> task = scheduler.currentTask(resident.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(SocialiseResidentGoal.ID, task.get().goalId(),
                "villager with no workplace falls through work/deliver/fetch to socialise in civic/flex windows");
    }

    @Test
    void schedulerWithOnlyIdleGoalReturnsIdleTask() {
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(new IdleResidentGoal()));
        SettlementResidentRecord resident = buildUnassignedVillager();

        scheduler.tick(new ResidentGoalContext(resident, null, DAY_TICK_ACTIVE));

        Optional<ResidentTask> task = scheduler.currentTask(resident.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(IdleResidentGoal.ID, task.get().goalId());
    }

    @Test
    void activeTaskAdvancesUntilMaxTicksThenTimesOut() {
        ResidentGoal fastGoal = new FixedDurationTestGoal("test/goal/fast", 50, 3, false);
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(fastGoal));
        SettlementResidentRecord resident = buildLocalWorker();
        UUID id = resident.residentUuid();

        scheduler.tick(new ResidentGoalContext(resident, null, 100L));
        assertTrue(scheduler.currentTask(id).isPresent());

        for (int i = 0; i < 3; i++) {
            scheduler.tick(new ResidentGoalContext(resident, null, 100L + i + 1));
        }

        Optional<ResidentTask> after = scheduler.currentTask(id);
        assertTrue(after.isPresent(), "task recorded for post-finish inspection");
        assertTrue(after.get().isDone());
        assertEquals(ResidentStopReason.TIMED_OUT, after.get().stopReason());
    }

    @Test
    void cooldownSkipsSameGoalAfterCompletion() {
        ResidentGoal coolingGoal = new FixedDurationTestGoal("test/goal/cooling", 99, 2, true);
        ResidentGoal fallback = new FixedDurationTestGoal("test/goal/fallback", 1, 1, false);
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(coolingGoal, fallback));
        SettlementResidentRecord resident = buildLocalWorker();
        UUID id = resident.residentUuid();

        scheduler.tick(new ResidentGoalContext(resident, null, 200L));
        scheduler.forceStop(id, ResidentStopReason.COMPLETED);

        scheduler.tick(new ResidentGoalContext(resident, null, 201L));

        Optional<ResidentTask> picked = scheduler.currentTask(id);
        assertTrue(picked.isPresent());
        assertEquals(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "test/goal/fallback"), picked.get().goalId(),
                "cooling goal should skip until its cooldown expires; fallback picked instead");
    }

    @Test
    void tieBreakFallsBackToLexicographicIdOrder() {
        ResidentGoal goalZ = new FixedDurationTestGoal("test/goal/z", 40, 5, false);
        ResidentGoal goalA = new FixedDurationTestGoal("test/goal/a", 40, 5, false);
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(goalZ, goalA));
        SettlementResidentRecord resident = buildLocalWorker();

        scheduler.tick(new ResidentGoalContext(resident, null, 300L));

        Optional<ResidentTask> picked = scheduler.currentTask(resident.residentUuid());
        assertTrue(picked.isPresent());
        assertEquals(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "test/goal/a"), picked.get().goalId(),
                "tie-break sorts by lexicographic full ID, registration order must not matter");
    }

    @Test
    void forceStopMarksTaskDoneWithProvidedReason() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        SettlementResidentRecord resident = buildLocalWorker();
        UUID id = resident.residentUuid();
        scheduler.tick(new ResidentGoalContext(resident, null, DAY_TICK_ACTIVE));

        scheduler.forceStop(id, ResidentStopReason.MANUALLY_STOPPED);

        Optional<ResidentTask> task = scheduler.currentTask(id);
        assertTrue(task.isPresent());
        assertTrue(task.get().isDone());
        assertEquals(ResidentStopReason.MANUALLY_STOPPED, task.get().stopReason());
    }

    @Test
    void resetClearsActiveTasksAndCooldowns() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        SettlementResidentRecord resident = buildLocalWorker();
        UUID id = resident.residentUuid();
        scheduler.tick(new ResidentGoalContext(resident, null, DAY_TICK_ACTIVE));
        assertNotNull(scheduler.currentTask(id).orElse(null));

        scheduler.reset();

        assertFalse(scheduler.currentTask(id).isPresent());
    }

    @Test
    void extendedDefaultGoalsPickGoHomeWhenResidentHasHomeBindingAtNight() {
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        BannerModSellerDispatchRuntime sellerRuntime = new BannerModSellerDispatchRuntime();
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                SettlementMarketState::empty,
                sellerRuntime
        );
        SettlementResidentRecord resident = buildLocalWorker();
        homeRuntime.assign(
                resident.residentUuid(),
                UUID.fromString("00000000-0000-0000-0000-0000000000b1"),
                com.talhanation.bannermod.settlement.household.HomePreference.ASSIGNED,
                100L
        );

        scheduler.tick(new ResidentGoalContext(resident, null, DAY_TICK_NIGHT));

        Optional<ResidentTask> task = scheduler.currentTask(resident.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(GoHomeResidentGoal.ID, task.get().goalId());
    }

    @Test
    void extendedDefaultGoalsPickSellerOverWorkWhenReadyDispatchExists() {
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        BannerModSellerDispatchRuntime sellerRuntime = new BannerModSellerDispatchRuntime();
        SettlementResidentRecord seller = buildMarketSeller();
        UUID marketUuid = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        SettlementMarketState marketState = new SettlementMarketState(
                1,
                1,
                16,
                8,
                1,
                1,
                List.of(),
                List.of(new SettlementSellerDispatchRecord(
                        seller.residentUuid(),
                        marketUuid,
                        "market",
                        SettlementSellerDispatchState.READY
                ))
        );
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                () -> marketState,
                sellerRuntime
        );

        scheduler.tick(new ResidentGoalContext(seller, null, DAY_TICK_ACTIVE));

        Optional<ResidentTask> task = scheduler.currentTask(seller.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(SellerResidentGoal.ID, task.get().goalId());
        assertTrue(sellerRuntime.phase(seller.residentUuid()).isPresent());
    }

    @Test
    void zeroPriorityGoalIsNotSelectedEvenIfCanStartReturnsTrue() {
        ResidentGoal zeroPriority = new FixedDurationTestGoal("test/goal/zero", 0, 5, false);
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(zeroPriority));
        SettlementResidentRecord resident = buildLocalWorker();

        scheduler.tick(new ResidentGoalContext(resident, null, 10L));

        assertFalse(scheduler.currentTask(resident.residentUuid()).isPresent());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static SettlementResidentRecord buildLocalWorker() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID workArea = UUID.fromString("00000000-0000-0000-0000-000000000099");
        return new SettlementResidentRecord(
                id,
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                "teamA",
                workArea,
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }

    private static SettlementResidentRecord buildUnassignedVillager() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return new SettlementResidentRecord(
                id,
                SettlementResidentRole.VILLAGER,
                SettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                SettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                SettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static SettlementResidentRecord buildMarketSeller() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID marketBuilding = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        return new SettlementResidentRecord(
                id,
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                new SettlementResidentServiceContract(
                        SettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                        marketBuilding,
                        "market"
                ),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000ab"),
                "teamA",
                marketBuilding,
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }

    /**
     * Deterministic stub used by tie-break, cooldown, timeout, and
     * zero-priority tests. Not meant to run in production.
     */
    private static final class FixedDurationTestGoal implements ResidentGoal {
        private final ResourceLocation id;
        private final int priority;
        private final int duration;
        private final boolean hasCooldown;

        FixedDurationTestGoal(String path, int priority, int duration, boolean hasCooldown) {
            this.id = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, path);
            this.priority = priority;
            this.duration = duration;
            this.hasCooldown = hasCooldown;
        }

        @Override
        public ResourceLocation id() {
            return this.id;
        }

        @Override
        public int computePriority(ResidentGoalContext ctx) {
            return this.priority;
        }

        @Override
        public boolean canStart(ResidentGoalContext ctx) {
            return true;
        }

        @Override
        public ResidentTask start(ResidentGoalContext ctx) {
            return new ResidentTask(this.id, ctx.gameTime(), this.duration);
        }

        @Override
        public int cooldownTicks() {
            return this.hasCooldown ? 1000 : 0;
        }
    }
}
