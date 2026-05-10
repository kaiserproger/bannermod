package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.SettlementProjectCandidateSnapshot;
import com.talhanation.bannermod.settlement.SettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.SettlementResidentMode;
import com.talhanation.bannermod.settlement.SettlementResidentRecord;
import com.talhanation.bannermod.settlement.SettlementResidentRole;
import com.talhanation.bannermod.settlement.SettlementResidentRuntimeRoleState;
import com.talhanation.bannermod.settlement.SettlementResidentScheduleSeed;
import com.talhanation.bannermod.settlement.SettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.SettlementResidentServiceContract;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignalState;
import com.talhanation.bannermod.settlement.SettlementTradeRouteHandoffSnapshot;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentStopReason;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.ResidentTaskOutcome;
import com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SeekSuppliesResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.household.GoHomeResidentGoal;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcSocietyDecisionSnapshotTest {
    @Test
    void recentTimedOutOutcomeAppearsAsBlockedRecoveryReason() {
        long gameTime = 6000L;
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-00000000d101");
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId),
                null,
                gameTime,
                NpcSocietyProfile.createDefault(residentId, gameTime)
                        .withNeedState(10, 10, 10, 10, gameTime)
        );

        NpcSocietyDecisionSnapshot snapshot = NpcSocietyDecisionSnapshot.capture(
                ctx,
                null,
                "NO_CLEAR_ROUTE",
                new ResidentTaskOutcome(WorkResidentGoal.ID, ResidentStopReason.TIMED_OUT, gameTime - 20L)
        );

        assertEquals("BLOCKED", snapshot.stateTag());
        assertEquals(WorkResidentGoal.ID.toString(), snapshot.blockedGoalId());
        assertEquals(NpcSocietyDecisionSnapshot.BLOCKED_REASON_TASK_TIMED_OUT, snapshot.blockedReasonTag());
    }

    @Test
    void invalidatedOutcomeKeepsBlockedReasonWhileFallbackExecutes() {
        long gameTime = 9100L;
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-00000000d104");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-00000000d124");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, gameTime)
                .withPhaseOneState(
                        null,
                        homeId,
                        UUID.fromString("00000000-0000-0000-0000-00000000d134"),
                        NpcDailyPhase.ACTIVE,
                        NpcIntent.WORK,
                        NpcAnchorType.WORKPLACE,
                        new NpcSocietyDecisionSnapshot("BLOCKED", null, "ASSIGNED_SHIFT", "HEADING_TO_WORKPLACE",
                                WorkResidentGoal.ID.toString(), NpcSocietyDecisionSnapshot.BLOCKED_REASON_CONTEXT_INVALIDATED,
                                NpcIntent.WORK.name(), gameTime - 30L),
                        gameTime
                )
                .withNeedState(10, 54, 16, 14, gameTime);
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId),
                null,
                gameTime,
                gameTime,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        NpcSocietyDecisionSnapshot snapshot = NpcSocietyDecisionSnapshot.capture(
                ctx,
                new ResidentTask(GoHomeResidentGoal.ID, gameTime, 40),
                "RETURNING_HOME_ROUTE",
                new ResidentTaskOutcome(WorkResidentGoal.ID, ResidentStopReason.CONTEXT_INVALID, gameTime - 10L)
        );

        assertEquals("EXECUTING", snapshot.stateTag());
        assertEquals(NpcSocietyDecisionSnapshot.BLOCKED_REASON_CONTEXT_INVALIDATED, snapshot.blockedReasonTag());
    }

    @Test
    void restChoiceNowExplainsRestWindowDirectly() {
        long gameTime = 15000L;
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-00000000d102");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-00000000d122");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, gameTime)
                .withPhaseOneState(
                        null,
                        homeId,
                        UUID.fromString("00000000-0000-0000-0000-00000000d132"),
                        NpcDailyPhase.REST,
                        NpcIntent.REST,
                        NpcAnchorType.HOME,
                        new NpcSocietyDecisionSnapshot("BLOCKED", null, "ASSIGNED_SHIFT", "HEADING_TO_WORKPLACE",
                                WorkResidentGoal.ID.toString(), "TASK_TIMED_OUT", NpcIntent.WORK.name(), gameTime - 40L),
                        gameTime
                )
                .withNeedState(10, 82, 12, 10, gameTime);
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId),
                null,
                gameTime,
                gameTime,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        NpcSocietyDecisionSnapshot snapshot = NpcSocietyDecisionSnapshot.capture(
                ctx,
                new ResidentTask(RestResidentGoal.ID, gameTime, 40),
                "RESTING_AT_HOME",
                null
        );

        assertEquals("REST_WINDOW", snapshot.choiceReasonTag());
    }

    @Test
    void fallbackTaskAfterFailureStillPublishesExecutingState() {
        long gameTime = 9200L;
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-00000000d103");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-00000000d123");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, gameTime)
                .withPhaseOneState(
                        null,
                        homeId,
                        UUID.fromString("00000000-0000-0000-0000-00000000d133"),
                        NpcDailyPhase.ACTIVE,
                        NpcIntent.WORK,
                        NpcAnchorType.WORKPLACE,
                        new NpcSocietyDecisionSnapshot("BLOCKED", null, "ASSIGNED_SHIFT", "HEADING_TO_WORKPLACE",
                                WorkResidentGoal.ID.toString(), "TASK_TIMED_OUT", NpcIntent.WORK.name(), gameTime - 40L),
                        gameTime
                )
                .withNeedState(12, 58, 18, 16, gameTime);
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId),
                null,
                gameTime,
                gameTime,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        NpcSocietyDecisionSnapshot snapshot = NpcSocietyDecisionSnapshot.capture(
                ctx,
                new ResidentTask(GoHomeResidentGoal.ID, gameTime, 40),
                "RETURNING_HOME_ROUTE",
                new ResidentTaskOutcome(WorkResidentGoal.ID, ResidentStopReason.TIMED_OUT, gameTime - 20L)
        );

        assertEquals("EXECUTING", snapshot.stateTag());
        assertEquals("HOMEWARD_PULL", snapshot.choiceReasonTag());
    }

    @Test
    void nightGoHomeChoiceExplainsRestWindowBeforeGenericFallback() {
        long gameTime = 15000L;
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-00000000d105");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-00000000d125");
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId),
                null,
                gameTime,
                gameTime,
                NpcSocietyProfile.createDefault(residentId, gameTime)
                        .withPhaseOneState(
                                null,
                                homeId,
                                null,
                                NpcDailyPhase.ACTIVE,
                                NpcIntent.UNSPECIFIED,
                                NpcAnchorType.NONE,
                                NpcSocietyDecisionSnapshot.empty(),
                                gameTime
                        )
                        .withNeedState(10, 95, 10, 10, gameTime),
                1,
                NpcHouseholdHousingState.NORMAL,
                false,
                0
        );

        NpcSocietyDecisionSnapshot snapshot = NpcSocietyDecisionSnapshot.capture(
                ctx,
                new ResidentTask(GoHomeResidentGoal.ID, gameTime, 40),
                "SOON_NIGHT_HOMEBOUND",
                null
        );

        assertEquals("REST_WINDOW", snapshot.choiceReasonTag());
    }

    @Test
    void supplyRunExplainsHomeFoodShortageWhenHouseStillExists() {
        long gameTime = 9400L;
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-00000000d104");
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-00000000d124");
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId),
                settlementWithStockpile(workerResident(residentId)),
                gameTime,
                gameTime,
                NpcSocietyProfile.createDefault(residentId, gameTime)
                        .withPhaseOneState(
                                null,
                                homeId,
                                UUID.fromString("00000000-0000-0000-0000-00000000d134"),
                                NpcDailyPhase.ACTIVE,
                                NpcIntent.SEEK_SUPPLIES,
                                NpcAnchorType.WORKPLACE,
                                NpcSocietyDecisionSnapshot.empty(),
                                gameTime
                        )
                        .withNeedState(74, 14, 10, 8, gameTime),
                2,
                NpcHouseholdHousingState.NORMAL,
                false,
                0
        );

        NpcSocietyDecisionSnapshot snapshot = NpcSocietyDecisionSnapshot.capture(
                ctx,
                new ResidentTask(SeekSuppliesResidentGoal.ID, gameTime, 40),
                "STOCKPILE_SUPPLY_RUN",
                null
        );

        assertEquals("HOME_FOOD_SHORTAGE", snapshot.choiceReasonTag());
    }

    private static SettlementResidentRecord workerResident(UUID residentId) {
        return new SettlementResidentRecord(
                residentId,
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.LABOR_DAY,
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                UUID.fromString("00000000-0000-0000-0000-00000000d111"),
                "team-test",
                UUID.fromString("00000000-0000-0000-0000-00000000d121"),
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }

    private static SettlementSnapshot settlementWithStockpile(SettlementResidentRecord resident) {
        return new SettlementSnapshot(
                UUID.fromString("00000000-0000-0000-0000-00000000d201"),
                0,
                0,
                null,
                9400L,
                4,
                4,
                1,
                1,
                0,
                0,
                SettlementStockpileSummary.empty(),
                new SettlementMarketState(1, 0, 0, 0, 0, 0, List.of(), List.of()),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(resident),
                List.of(new SettlementBuildingRecord(
                        UUID.fromString("00000000-0000-0000-0000-00000000d202"),
                        "bannermod:stockpile",
                        new BlockPos(4, 64, 4),
                        null,
                        null,
                        0,
                        0,
                        0,
                        List.of(),
                        true,
                        1,
                        27,
                        false,
                        false,
                        List.of("food")
                ))
        );
    }
}
