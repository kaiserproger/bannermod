package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.society.NpcAnchorType;
import com.talhanation.bannermod.society.NpcDailyPhase;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyDecisionSnapshot;
import com.talhanation.bannermod.society.NpcSocietyProfile;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentMode;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRole;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRuntimeRoleSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentServiceContract;
import com.talhanation.bannermod.settlement.BannerModSettlementSellerDispatchRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementSellerDispatchState;
import com.talhanation.bannermod.settlement.BannerModSettlementServiceActorState;
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
        BannerModSettlementResidentRecord worker = buildLocalWorker();
        ResidentGoalContext ctx = new ResidentGoalContext(worker, null, DAY_TICK_ACTIVE);

        scheduler.tick(ctx);

        Optional<ResidentTask> task = scheduler.currentTask(worker.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(WorkResidentGoal.ID, task.get().goalId());
    }

    @Test
    void nightTickSelectsRestOverIdle() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        BannerModSettlementResidentRecord resident = buildLocalWorker();
        ResidentGoalContext ctx = new ResidentGoalContext(resident, null, DAY_TICK_NIGHT);

        scheduler.tick(ctx);

        Optional<ResidentTask> task = scheduler.currentTask(resident.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(RestResidentGoal.ID, task.get().goalId());
    }

    @Test
    void unassignedVillagerInDaylightFlexSocialisesRatherThanWorks() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        BannerModSettlementResidentRecord resident = buildUnassignedVillager();
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
        BannerModSettlementResidentRecord resident = buildUnassignedVillager();

        scheduler.tick(new ResidentGoalContext(resident, null, DAY_TICK_ACTIVE));

        Optional<ResidentTask> task = scheduler.currentTask(resident.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(IdleResidentGoal.ID, task.get().goalId());
    }

    @Test
    void activeTaskAdvancesUntilMaxTicksThenTimesOut() {
        ResidentGoal fastGoal = new FixedDurationTestGoal("test/goal/fast", 50, 3, false);
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(fastGoal));
        BannerModSettlementResidentRecord resident = buildLocalWorker();
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
        BannerModSettlementResidentRecord resident = buildLocalWorker();
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
        BannerModSettlementResidentRecord resident = buildLocalWorker();

        scheduler.tick(new ResidentGoalContext(resident, null, 300L));

        Optional<ResidentTask> picked = scheduler.currentTask(resident.residentUuid());
        assertTrue(picked.isPresent());
        assertEquals(ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "test/goal/a"), picked.get().goalId(),
                "tie-break sorts by lexicographic full ID, registration order must not matter");
    }

    @Test
    void schedulerPrefersContinuingPreviousGoalWhenAlternativeIsOnlySlightlyBetter() {
        ResidentGoal steady = new FixedDurationTestGoal("test/goal/steady", 50, 5, false);
        ResidentGoal rival = new FixedDurationTestGoal("test/goal/rival", 57, 5, false);
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(steady, rival));
        BannerModSettlementResidentRecord resident = buildLocalWorker();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(resident.residentUuid(), DAY_TICK_ACTIVE)
                .withPhaseOneState(
                        null,
                        null,
                        null,
                        NpcDailyPhase.ACTIVE,
                        NpcIntent.WORK,
                        NpcAnchorType.WORKPLACE,
                        new NpcSocietyDecisionSnapshot("EXECUTING", steady.id().toString(), "ASSIGNED_SHIFT", "HEADING_TO_WORKPLACE", null, "NONE", NpcIntent.WORK.name(), DAY_TICK_ACTIVE - 80L),
                        DAY_TICK_ACTIVE
                );

        scheduler.tick(new ResidentGoalContext(resident, null, DAY_TICK_ACTIVE, profile));

        Optional<ResidentTask> picked = scheduler.currentTask(resident.residentUuid());
        assertTrue(picked.isPresent());
        assertEquals(steady.id(), picked.get().goalId(),
                "scheduler should keep the previous goal when the competing goal is only marginally better");
    }

    @Test
    void schedulerKeepsRestLoopGoalAgainstModeratelyBetterAlternative() {
        ResidentGoal rival = new FixedDurationTestGoal("test/goal/rival", 110, 5, false);
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(new RestResidentGoal(), rival));
        BannerModSettlementResidentRecord resident = buildLocalWorker();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(resident.residentUuid(), DAY_TICK_NIGHT)
                .withPhaseOneState(
                        null,
                        null,
                        null,
                        NpcDailyPhase.REST,
                        NpcIntent.REST,
                        NpcAnchorType.HOME,
                        new NpcSocietyDecisionSnapshot("EXECUTING", RestResidentGoal.ID.toString(), "REST_WINDOW", "RESTING_AT_HOME", null, "NONE", NpcIntent.GO_HOME.name(), DAY_TICK_NIGHT - 100L),
                        DAY_TICK_NIGHT
                );

        scheduler.tick(new ResidentGoalContext(resident, null, DAY_TICK_NIGHT, profile));

        Optional<ResidentTask> picked = scheduler.currentTask(resident.residentUuid());
        assertTrue(picked.isPresent());
        assertEquals(RestResidentGoal.ID, picked.get().goalId(),
                "rest-like routine goals should require a much larger advantage before switching away");
    }

    @Test
    void forceStopMarksTaskDoneWithProvidedReason() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        BannerModSettlementResidentRecord resident = buildLocalWorker();
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
        BannerModSettlementResidentRecord resident = buildLocalWorker();
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
                BannerModSettlementMarketState::empty,
                sellerRuntime
        );
        BannerModSettlementResidentRecord resident = buildLocalWorker();
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
        BannerModSettlementResidentRecord seller = buildMarketSeller();
        UUID marketUuid = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        BannerModSettlementMarketState marketState = new BannerModSettlementMarketState(
                1,
                1,
                16,
                8,
                1,
                1,
                List.of(),
                List.of(new BannerModSettlementSellerDispatchRecord(
                        seller.residentUuid(),
                        marketUuid,
                        "market",
                        BannerModSettlementSellerDispatchState.READY
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
    void laborWorkerSocialisesDuringLeisureGapAfterWorkHours() {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        BannerModSettlementResidentRecord resident = buildLocalWorker();
        long leisureTick = 10000L;
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(resident.residentUuid(), leisureTick)
                .withNeedState(10, 12, 92, 8, leisureTick)
                .withSocialState(50, 0, 0, 0, 55, leisureTick);

        scheduler.tick(new ResidentGoalContext(resident, null, leisureTick, profile));

        Optional<ResidentTask> task = scheduler.currentTask(resident.residentUuid());
        assertTrue(task.isPresent());
        assertEquals(SocialiseResidentGoal.ID, task.get().goalId(),
                "workers should use the post-shift leisure gap for readable social behavior instead of dropping straight to idle");
    }

    @Test
    void goHomeChainCanSettleIntoRestAfterExtendedReturnWindow() {
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        BannerModSellerDispatchRuntime sellerRuntime = new BannerModSellerDispatchRuntime();
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                BannerModSettlementMarketState::empty,
                sellerRuntime
        );
        BannerModSettlementResidentRecord resident = buildLocalWorker();
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
        homeRuntime.assign(resident.residentUuid(), homeId,
                com.talhanation.bannermod.settlement.household.HomePreference.ASSIGNED,
                DAY_TICK_NIGHT - 200L);
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(resident.residentUuid(), DAY_TICK_NIGHT)
                .withPhaseOneState(
                        null,
                        homeId,
                        null,
                        NpcDailyPhase.RETURNING_HOME,
                        NpcIntent.GO_HOME,
                        NpcAnchorType.HOME,
                        new NpcSocietyDecisionSnapshot("EXECUTING", GoHomeResidentGoal.ID.toString(), "REST_WINDOW", "SOON_NIGHT_HOMEBOUND", null, "NONE", NpcIntent.WORK.name(), DAY_TICK_NIGHT - 120L),
                        DAY_TICK_NIGHT
                );

        scheduler.tick(new ResidentGoalContext(resident, null, DAY_TICK_NIGHT, profile));

        Optional<ResidentTask> picked = scheduler.currentTask(resident.residentUuid());
        assertTrue(picked.isPresent());
        assertEquals(RestResidentGoal.ID, picked.get().goalId(),
                "residents should stop endlessly re-picking go-home and settle into rest once the return-home window has run long enough");
    }

    @Test
    void leaveHomeChainCanFanOutIntoWorkAfterBriefDeparture() {
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        BannerModSellerDispatchRuntime sellerRuntime = new BannerModSellerDispatchRuntime();
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                BannerModSettlementMarketState::empty,
                sellerRuntime
        );
        BannerModSettlementResidentRecord resident = buildLocalWorker();
        long morningTick = 1080L;
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-0000000000b4");
        homeRuntime.assign(resident.residentUuid(), homeId,
                com.talhanation.bannermod.settlement.household.HomePreference.ASSIGNED,
                morningTick - 100L);
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(resident.residentUuid(), morningTick)
                .withPhaseOneState(
                        null,
                        homeId,
                        resident.boundWorkAreaUuid(),
                        NpcDailyPhase.DEPARTING_HOME,
                        NpcIntent.LEAVE_HOME,
                        NpcAnchorType.STREET,
                        new NpcSocietyDecisionSnapshot("EXECUTING", com.talhanation.bannermod.settlement.household.LeaveHomeResidentGoal.ID.toString(), "EARLY_ACTIVE_WINDOW", "LEAVING_HOME_FOR_WORK", null, "NONE", NpcIntent.REST.name(), morningTick - 70L),
                        morningTick
                );

        scheduler.tick(new ResidentGoalContext(resident, null, morningTick, profile));

        Optional<ResidentTask> picked = scheduler.currentTask(resident.residentUuid());
        assertTrue(picked.isPresent());
        assertEquals(WorkResidentGoal.ID, picked.get().goalId(),
                "residents should leave home first, then fan out into real work instead of lingering on the leave-home bridge goal too long");
    }

    @Test
    void zeroPriorityGoalIsNotSelectedEvenIfCanStartReturnsTrue() {
        ResidentGoal zeroPriority = new FixedDurationTestGoal("test/goal/zero", 0, 5, false);
        BannerModResidentGoalScheduler scheduler = new BannerModResidentGoalScheduler(List.of(zeroPriority));
        BannerModSettlementResidentRecord resident = buildLocalWorker();

        scheduler.tick(new ResidentGoalContext(resident, null, 10L));

        assertFalse(scheduler.currentTask(resident.residentUuid()).isPresent());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static BannerModSettlementResidentRecord buildLocalWorker() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID workArea = UUID.fromString("00000000-0000-0000-0000-000000000099");
        return new BannerModSettlementResidentRecord(
                id,
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentRuntimeRoleSeed.LOCAL_LABOR,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                "teamA",
                workArea,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }

    private static BannerModSettlementResidentRecord buildUnassignedVillager() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return new BannerModSettlementResidentRecord(
                id,
                BannerModSettlementResidentRole.VILLAGER,
                BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                BannerModSettlementResidentRuntimeRoleSeed.VILLAGE_LIFE,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static BannerModSettlementResidentRecord buildMarketSeller() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID marketBuilding = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        return new BannerModSettlementResidentRecord(
                id,
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentRuntimeRoleSeed.LOCAL_LABOR,
                new BannerModSettlementResidentServiceContract(
                        BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE,
                        marketBuilding,
                        "market"
                ),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000ab"),
                "teamA",
                marketBuilding,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
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
