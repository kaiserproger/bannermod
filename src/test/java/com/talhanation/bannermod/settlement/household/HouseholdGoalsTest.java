package com.talhanation.bannermod.settlement.household;

import com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentMode;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRole;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRuntimeRoleState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentServiceContract;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdGoalsTest {

    // LABOR_DAY: activeStart=1000, activeEnd=9000, restStart=12000, restEnd=23999.
    private static final long DAY_TICK_EARLY_ACTIVE = 1200L;    // within activeStart .. activeStart + 500
    private static final long DAY_TICK_MID_ACTIVE = 6000L;      // active but past the "early" window
    private static final long DAY_TICK_APPROACH_REST = 11700L;  // within restStart - 500 .. restStart
    private static final long DAY_TICK_REST = 15000L;           // deep inside rest
    private static final UUID RESIDENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID HOME_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void goHomeCanStartFalseWithoutHomeAssignment() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        GoHomeResidentGoal goal = new GoHomeResidentGoal(runtime);
        ResidentGoalContext ctx = new ResidentGoalContext(buildResident(), null, DAY_TICK_REST);

        assertFalse(goal.canStart(ctx));
        assertEquals(0, goal.computePriority(ctx));
    }

    @Test
    void goHomeCanStartTrueInRestPhaseWithHomeAssignment() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        runtime.assign(RESIDENT_ID, HOME_ID, HomePreference.ASSIGNED, 0L);
        GoHomeResidentGoal goal = new GoHomeResidentGoal(runtime);
        ResidentGoalContext ctx = new ResidentGoalContext(buildResident(), null, DAY_TICK_REST);

        assertTrue(goal.canStart(ctx));
        assertTrue(goal.computePriority(ctx) > 0);
    }

    @Test
    void goHomeFiresWhenApproachingRest() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        runtime.assign(RESIDENT_ID, HOME_ID, HomePreference.ASSIGNED, 0L);
        GoHomeResidentGoal goal = new GoHomeResidentGoal(runtime);
        ResidentGoalContext ctx = new ResidentGoalContext(buildResident(), null, DAY_TICK_APPROACH_REST);

        assertTrue(goal.canStart(ctx));
        assertTrue(goal.computePriority(ctx) > 0);
    }

    @Test
    void goHomeIgnoresMidActiveEvenWithHome() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        runtime.assign(RESIDENT_ID, HOME_ID, HomePreference.ASSIGNED, 0L);
        GoHomeResidentGoal goal = new GoHomeResidentGoal(runtime);
        ResidentGoalContext ctx = new ResidentGoalContext(buildResident(), null, DAY_TICK_MID_ACTIVE);

        assertFalse(goal.canStart(ctx));
        assertEquals(0, goal.computePriority(ctx));
    }

    @Test
    void leaveHomeFiresOnlyInEarlyActivePhaseWithHomeAssignment() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        runtime.assign(RESIDENT_ID, HOME_ID, HomePreference.ASSIGNED, 0L);
        LeaveHomeResidentGoal goal = new LeaveHomeResidentGoal(runtime);

        ResidentGoalContext earlyActive = new ResidentGoalContext(buildResident(), null, DAY_TICK_EARLY_ACTIVE);
        ResidentGoalContext midActive = new ResidentGoalContext(buildResident(), null, DAY_TICK_MID_ACTIVE);
        ResidentGoalContext rest = new ResidentGoalContext(buildResident(), null, DAY_TICK_REST);

        assertTrue(goal.canStart(earlyActive));
        assertTrue(goal.computePriority(earlyActive) > 0);

        assertFalse(goal.canStart(midActive));
        assertEquals(0, goal.computePriority(midActive));

        assertFalse(goal.canStart(rest));
        assertEquals(0, goal.computePriority(rest));
    }

    @Test
    void leaveHomeStaysSilentWithoutHomeAssignment() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        LeaveHomeResidentGoal goal = new LeaveHomeResidentGoal(runtime);
        ResidentGoalContext ctx = new ResidentGoalContext(buildResident(), null, DAY_TICK_EARLY_ACTIVE);

        assertFalse(goal.canStart(ctx));
        assertEquals(0, goal.computePriority(ctx));
    }

    @Test
    void priorityOrderingIsStableAcrossRepeatedCallsForSameContext() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        runtime.assign(RESIDENT_ID, HOME_ID, HomePreference.ASSIGNED, 0L);
        GoHomeResidentGoal goHome = new GoHomeResidentGoal(runtime);
        LeaveHomeResidentGoal leaveHome = new LeaveHomeResidentGoal(runtime);

        ResidentGoalContext rest = new ResidentGoalContext(buildResident(), null, DAY_TICK_REST);
        ResidentGoalContext earlyActive = new ResidentGoalContext(buildResident(), null, DAY_TICK_EARLY_ACTIVE);

        int goHomeRest1 = goHome.computePriority(rest);
        int goHomeRest2 = goHome.computePriority(rest);
        int leaveEarly1 = leaveHome.computePriority(earlyActive);
        int leaveEarly2 = leaveHome.computePriority(earlyActive);

        assertEquals(goHomeRest1, goHomeRest2, "go-home priority must be deterministic");
        assertEquals(leaveEarly1, leaveEarly2, "leave-home priority must be deterministic");

        // During rest, go-home should out-rank leave-home (which is gated to active phase).
        assertTrue(goHomeRest1 > leaveHome.computePriority(rest));
        // During early active, leave-home should out-rank go-home (which is gated to rest/approach).
        assertTrue(leaveEarly1 > goHome.computePriority(earlyActive));
    }

    @Test
    void startReturnsTasksWithExpectedDurations() {
        BannerModHomeAssignmentRuntime runtime = new BannerModHomeAssignmentRuntime();
        runtime.assign(RESIDENT_ID, HOME_ID, HomePreference.ASSIGNED, 0L);
        GoHomeResidentGoal goHome = new GoHomeResidentGoal(runtime);
        LeaveHomeResidentGoal leaveHome = new LeaveHomeResidentGoal(runtime);

        ResidentGoalContext rest = new ResidentGoalContext(buildResident(), null, DAY_TICK_REST);
        ResidentGoalContext earlyActive = new ResidentGoalContext(buildResident(), null, DAY_TICK_EARLY_ACTIVE);

        assertEquals(120, goHome.start(rest).maxTicks());
        assertEquals(60, leaveHome.start(earlyActive).maxTicks());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static BannerModSettlementResidentRecord buildResident() {
        return new BannerModSettlementResidentRecord(
                RESIDENT_ID,
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY,
                BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                "teamA",
                UUID.fromString("00000000-0000-0000-0000-0000000000dd"),
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }
}
