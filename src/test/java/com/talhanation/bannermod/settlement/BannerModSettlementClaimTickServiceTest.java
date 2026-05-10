package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.society.NpcAnchorType;
import com.talhanation.bannermod.society.NpcDailyPhase;
import com.talhanation.bannermod.society.NpcHouseholdHousingState;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyAnchorGoal;
import com.talhanation.bannermod.society.NpcSocietyDecisionSnapshot;
import com.talhanation.bannermod.society.NpcSocietyProfile;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentStopReason;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.job.JobHandlerRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModSettlementClaimTickServiceTest {

    @Test
    void routeInvalidationSignalForceStopsActiveTaskAsContextInvalid() {
        SettlementOrchestrator.LevelRuntimeState state = SettlementOrchestrator.detachedStateForTests(JobHandlerRegistry.defaults());
        SettlementResidentRecord resident = buildLocalWorker();
        long gameTime = 6000L;
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(resident.residentUuid(), gameTime)
                .withPhaseOneState(
                        null,
                        UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
                        resident.boundWorkAreaUuid(),
                        NpcDailyPhase.ACTIVE,
                        NpcIntent.WORK,
                        NpcAnchorType.WORKPLACE,
                        new NpcSocietyDecisionSnapshot("EXECUTING", WorkResidentGoal.ID.toString(), "ASSIGNED_SHIFT", "HEADING_TO_WORKPLACE", null, "NONE", NpcIntent.LEAVE_HOME.name(), gameTime - 40L),
                        gameTime - 40L
                );
        ResidentGoalContext ctx = new ResidentGoalContext(
                resident,
                null,
                gameTime,
                gameTime,
                profile,
                0,
                NpcHouseholdHousingState.NORMAL,
                false,
                0
        );

        state.goalScheduler.tick(ctx);
        Optional<ResidentTask> started = state.goalScheduler.currentTask(resident.residentUuid());
        assertTrue(started.isPresent());
        assertEquals(WorkResidentGoal.ID, started.get().goalId());
        assertFalse(started.get().isDone());

        NpcSocietyAnchorGoal.signalRouteInvalidation(resident.residentUuid(), NpcIntent.WORK, gameTime);
        SettlementClaimTickService.applyRouteInvalidationIfNeeded(state, ctx);

        Optional<ResidentTask> stopped = state.goalScheduler.currentTask(resident.residentUuid());
        assertTrue(stopped.isPresent());
        assertTrue(stopped.get().isDone());
        assertEquals(ResidentStopReason.CONTEXT_INVALID, stopped.get().stopReason());
        assertEquals(ResidentStopReason.CONTEXT_INVALID,
                state.goalScheduler.lastOutcome(resident.residentUuid()).orElseThrow().stopReason());
    }

    private static SettlementResidentRecord buildLocalWorker() {
        return new SettlementResidentRecord(
                UUID.fromString("00000000-0000-0000-0000-0000000000a2"),
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-0000000000c2"),
                "teamA",
                UUID.fromString("00000000-0000-0000-0000-0000000000d2"),
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }
}
