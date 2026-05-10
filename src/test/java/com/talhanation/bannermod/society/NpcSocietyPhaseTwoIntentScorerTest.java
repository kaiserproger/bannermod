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
import com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSocietyPhaseTwoIntentScorerTest {
    private static final long ACTIVE_TIME = 6000L;
    private static final long REST_TIME = 15000L;

    @Test
    void fearAxisNoLongerTriggersHideDuringActivePhaseByItself() {
        ResidentGoalContext ctx = context(
                villagerResident(),
                ACTIVE_TIME,
                null,
                NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a001"), ACTIVE_TIME)
                        .withNeedState(5, 5, 5, 12, ACTIVE_TIME)
        );

        int hide = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.HIDE);
        int rest = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.REST);

        assertEquals(0, hide, "hide should now key off coarse safety pressure instead of fear-memory weighting");
        assertEquals(0, rest, "rest should stay inactive during the day without actual fatigue or rest-window pressure");
    }

    @Test
    void restGoalBasePriorityIsOnlyAppliedInRestPhase() {
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a002"), ACTIVE_TIME)
                .withNeedState(5, 5, 5, 12, ACTIVE_TIME);
        RestResidentGoal goal = new RestResidentGoal();

        int activePriority = goal.computePriority(context(villagerResident(), ACTIVE_TIME, null, profile));
        int restPriority = goal.computePriority(context(villagerResident(), REST_TIME, null, profile));

        assertEquals(0, activePriority, "rest goal should now stay fully inactive during active time without real rest pressure");
        assertEquals(87, restPriority, "rest phase should still receive a strong overnight priority in the cheaper scorer");
        assertTrue(restPriority > activePriority, "rest priority must jump sharply once the resident is in rest phase");
    }

    @Test
    void foodAccessEnablesEatAndSevereHungerBeatsWork() {
        SettlementResidentRecord worker = workerResident();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a005"), ACTIVE_TIME)
                .withNeedState(92, 10, 10, 10, ACTIVE_TIME);

        ResidentGoalContext noMarket = context(worker, ACTIVE_TIME, settlementWithOpenMarkets(worker, 0), profile);
        ResidentGoalContext openMarket = context(worker, ACTIVE_TIME, settlementWithOpenMarkets(worker, 1), profile);

        int eatWithoutMarket = NpcSocietyPhaseTwoIntentScorer.scoreIntent(noMarket, NpcIntent.EAT);
        int eatWithMarket = NpcSocietyPhaseTwoIntentScorer.scoreIntent(openMarket, NpcIntent.EAT);
        int workWithMarket = NpcSocietyPhaseTwoIntentScorer.scoreIntent(openMarket, NpcIntent.WORK);

        assertEquals(0, eatWithoutMarket, "eat should stay unavailable when the resident has no home and no market access");
        assertTrue(eatWithMarket > 0, "severe hunger with food access should still produce a strong eat score");
        assertTrue(eatWithMarket > workWithMarket, "severe hunger should out-rank work once food is reachable");
    }

    @Test
    void moderateNeedsKeepAssignedWorkersOnShift() {
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a005");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d005");
        SettlementResidentRecord worker = workerResident(residentId);
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withPhaseOneState(null, homeId, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE,
                        NpcSocietyDecisionSnapshot.empty(), ACTIVE_TIME)
                .withNeedState(60, 72, 40, 10, ACTIVE_TIME);
        ResidentGoalContext ctx = context(worker, ACTIVE_TIME, settlementWithOpenMarkets(worker, 1), profile);

        int work = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.WORK);
        int eat = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.EAT);
        int goHome = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.GO_HOME);
        int rest = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.REST);

        assertTrue(work > 0, "assigned workers should keep a live work score under moderate daytime pressure");
        assertEquals(0, eat, "moderate hunger should no longer interrupt work before hunger becomes strong");
        assertEquals(0, goHome, "moderate fatigue should no longer pull workers home during the day");
        assertEquals(0, rest, "daytime rest should stay off until fatigue becomes strong");
    }

    @Test
    void governorDangerStillPrefersRestrictedDefendRole() {
        ResidentGoalContext ctx = context(
                governorResident(),
                ACTIVE_TIME,
                null,
                NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a006"), ACTIVE_TIME)
                        .withNeedState(10, 10, 10, 40, ACTIVE_TIME)
        );

        int hide = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.HIDE);
        int defend = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.DEFEND);

        assertTrue(hide > 0, "danger should still activate hide pressure even for defenders");
        assertTrue(defend > hide, "restricted defender roles should still keep their coarse defend fallback under danger");
    }

    @Test
    void familyContextNoLongerChangesGoHomeScore() {
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a007");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d007");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withPhaseOneState(null, homeId, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE,
                        NpcSocietyDecisionSnapshot.empty(), ACTIVE_TIME)
                .withNeedState(10, 76, 15, 28, ACTIVE_TIME);

        ResidentGoalContext alone = context(villagerResident(residentId), ACTIVE_TIME, null, profile);
        ResidentGoalContext family = new ResidentGoalContext(
                villagerResident(residentId),
                null,
                ACTIVE_TIME,
                ACTIVE_TIME,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        int aloneScore = NpcSocietyPhaseTwoIntentScorer.scoreIntent(alone, NpcIntent.GO_HOME);
        int familyScore = NpcSocietyPhaseTwoIntentScorer.scoreIntent(family, NpcIntent.GO_HOME);

        assertEquals(aloneScore, familyScore,
                "family metadata should no longer act as a broad go-home runtime multiplier");
    }

    @Test
    void fearfulMemoryNoLongerChangesWorkOrHideScoring() {
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a008");
        NpcSocietyProfile calmProfile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withNeedState(18, 12, 18, 18, ACTIVE_TIME);
        NpcSocietyProfile fearfulProfile = calmProfile;

        int calmWork = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(workerResident(), ACTIVE_TIME, null, calmProfile), NpcIntent.WORK);
        int fearfulWork = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(workerResident(), ACTIVE_TIME, null, fearfulProfile), NpcIntent.WORK);
        int fearfulHide = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(workerResident(), ACTIVE_TIME, null, fearfulProfile), NpcIntent.HIDE);

        assertEquals(calmWork, fearfulWork,
                "memory axes should no longer suppress ordinary work scoring");
        assertEquals(0, fearfulHide,
                "fear-memory alone should not create a hide score without live safety pressure");
    }

    @Test
    void eveningWindowStrengthensGoHomePressureBeforeRest() {
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a010");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d010");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withPhaseOneState(null, homeId, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE,
                        NpcSocietyDecisionSnapshot.empty(), ACTIVE_TIME)
                .withNeedState(10, 20, 20, 10, ACTIVE_TIME);

        int middayScore = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(villagerResident(residentId), ACTIVE_TIME, null, profile), NpcIntent.GO_HOME);
        int eveningScore = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(villagerResident(residentId), 11550L, null, profile), NpcIntent.GO_HOME);

        assertTrue(eveningScore > middayScore,
                "go-home pressure should rise as the rest window approaches so NPCs start pulling back toward home");
    }

    @Test
    void recentGoHomeHistoryKeepsHomewardIntentMoreStable() {
        long time = 11550L;
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a012");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d012");
        NpcSocietyProfile neutralProfile = NpcSocietyProfile.createDefault(residentId, time)
                .withPhaseOneState(null, homeId, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE,
                        NpcSocietyDecisionSnapshot.empty(), time)
                .withNeedState(10, 28, 12, 8, time);
        NpcSocietyProfile stickyProfile = neutralProfile.withPhaseOneState(
                null,
                homeId,
                null,
                NpcDailyPhase.RETURNING_HOME,
                NpcIntent.GO_HOME,
                NpcAnchorType.HOME,
                new NpcSocietyDecisionSnapshot("EXECUTING", "bannermod:resident/goal/go_home", "REST_WINDOW", "SOON_NIGHT_HOMEBOUND", null, "NONE", NpcIntent.WORK.name(), time - 40L),
                time
        );

        int neutral = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(villagerResident(residentId), time, null, neutralProfile), NpcIntent.GO_HOME);
        int sticky = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(villagerResident(residentId), time, null, stickyProfile), NpcIntent.GO_HOME);

        assertTrue(sticky > neutral,
                "a resident already heading home should keep a small stability edge instead of immediately reconsidering on every near-tie");
    }

    @Test
    void dependentMetadataNoLongerChangesHideScore() {
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a013");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withNeedState(8, 8, 8, 55, ACTIVE_TIME);
        ResidentGoalContext noDependents = context(governorResident(), ACTIVE_TIME, null, profile);
        ResidentGoalContext withDependents = new ResidentGoalContext(
                governorResident(),
                null,
                ACTIVE_TIME,
                ACTIVE_TIME,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        int hideWithoutDependents = NpcSocietyPhaseTwoIntentScorer.scoreIntent(noDependents, NpcIntent.HIDE);
        int hideWithDependents = NpcSocietyPhaseTwoIntentScorer.scoreIntent(withDependents, NpcIntent.HIDE);

        assertTrue(hideWithoutDependents > 0);
        assertEquals(hideWithoutDependents, hideWithDependents,
                "dependent metadata should no longer alter hide scoring under the cheap safety-first model");
    }

    @Test
    void recentWorkFailureCanTemporarilyPullFamilyResidentHome() {
        long gameTime = 9200L;
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a014");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d014");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, gameTime)
                .withPhaseOneState(
                        null,
                        homeId,
                        uuid("00000000-0000-0000-0000-00000000e014"),
                        NpcDailyPhase.ACTIVE,
                        NpcIntent.WORK,
                        NpcAnchorType.WORKPLACE,
                        new NpcSocietyDecisionSnapshot("BLOCKED", null, "ASSIGNED_SHIFT", "HEADING_TO_WORKPLACE",
                                WorkResidentGoal.ID.toString(), "TASK_TIMED_OUT", NpcIntent.WORK.name(), gameTime - 60L),
                        gameTime
                )
                .withNeedState(16, 84, 24, 18, gameTime);
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(),
                null,
                gameTime,
                gameTime,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        int goHome = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.GO_HOME);
        int work = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.WORK);

        assertTrue(goHome > work,
                "after a timed-out work attempt, a tired family-linked resident should be allowed to regroup at home before work reasserts itself");
    }

    @Test
    void failedMealCanEscalateToSupplyRunEvenWhenResidentHasHome() {
        long time = 9400L;
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a016");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d016");
        SettlementResidentRecord worker = workerResident(residentId);
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, time)
                .withPhaseOneState(
                        null,
                        homeId,
                        uuid("00000000-0000-0000-0000-00000000e016"),
                        NpcDailyPhase.ACTIVE,
                        NpcIntent.EAT,
                        NpcAnchorType.HOME,
                        new NpcSocietyDecisionSnapshot("BLOCKED", null, "HUNGER_PRESSURE", "MEAL_AT_HOME",
                                "bannermod:resident/goal/eat", "TASK_TIMED_OUT", NpcIntent.WORK.name(), time - 60L),
                        time
                )
                .withNeedState(84, 16, 12, 8, time);
        ResidentGoalContext ctx = new ResidentGoalContext(
                worker,
                settlementWithStockpile(worker),
                time,
                time,
                profile,
                3,
                NpcHouseholdHousingState.NORMAL,
                true,
                1
        );

        int eat = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.EAT);
        int supplies = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.SEEK_SUPPLIES);

        assertTrue(supplies > eat,
                "after a failed meal attempt, a hungry resident with stockpile access should switch to a supply run instead of hammering the same eat path again");
    }

    @Test
    void freshGoHomeRecoverySuppressesImmediateWorkRetry() {
        long time = 9300L;
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a017");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d017");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, time)
                .withPhaseOneState(
                        null,
                        homeId,
                        uuid("00000000-0000-0000-0000-00000000e017"),
                        NpcDailyPhase.RETURNING_HOME,
                        NpcIntent.GO_HOME,
                        NpcAnchorType.HOME,
                        new NpcSocietyDecisionSnapshot("RECOVERING", "bannermod:resident/goal/go_home",
                                "RETURNING_TO_HOUSEHOLD", "REGROUPING_AT_HOME",
                                WorkResidentGoal.ID.toString(), NpcSocietyDecisionSnapshot.BLOCKED_REASON_CONTEXT_INVALIDATED,
                                NpcIntent.WORK.name(), time - 40L),
                        time
                )
                .withNeedState(18, 54, 20, 18, time);
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId),
                null,
                time,
                time,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        int goHome = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.GO_HOME);
        int work = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.WORK);

        assertTrue(goHome > work,
                "once a resident is already regrouping at home after a broken work route, fresh work pressure should stay suppressed until that recovery move settles");
    }

    @Test
    void invalidatedMealRecoveryPrefersSupplyRunOverAnotherMealRetry() {
        long time = 9400L;
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a018");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d018");
        SettlementResidentRecord worker = workerResident(residentId);
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, time)
                .withPhaseOneState(
                        null,
                        homeId,
                        uuid("00000000-0000-0000-0000-00000000e018"),
                        NpcDailyPhase.ACTIVE,
                        NpcIntent.EAT,
                        NpcAnchorType.HOME,
                        new NpcSocietyDecisionSnapshot("BLOCKED", null, "HUNGER_PRESSURE", "MEAL_AT_HOME",
                                "bannermod:resident/goal/eat", NpcSocietyDecisionSnapshot.BLOCKED_REASON_CONTEXT_INVALIDATED,
                                NpcIntent.WORK.name(), time - 60L),
                        time
                )
                .withNeedState(82, 18, 10, 12, time);
        ResidentGoalContext ctx = new ResidentGoalContext(
                worker,
                settlementWithStockpile(worker),
                time,
                time,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        int eat = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.EAT);
        int supplies = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.SEEK_SUPPLIES);

        assertTrue(supplies > eat,
                "when a meal path breaks because the context changed, the resident should switch harder into supply recovery instead of repeating the same home-meal plan");
    }

    private static ResidentGoalContext context(SettlementResidentRecord resident,
                                               long gameTime,
                                               SettlementSnapshot settlement,
                                               NpcSocietyProfile profile) {
        return new ResidentGoalContext(resident, settlement, gameTime, profile);
    }

    private static SettlementResidentRecord villagerResident() {
        return villagerResident(uuid("00000000-0000-0000-0000-00000000b001"));
    }

    private static SettlementResidentRecord villagerResident(UUID residentId) {
        return new SettlementResidentRecord(
                residentId,
                SettlementResidentRole.VILLAGER,
                SettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                SettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                SettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static SettlementResidentRecord workerResident() {
        return workerResident(uuid("00000000-0000-0000-0000-00000000b002"));
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
                uuid("00000000-0000-0000-0000-00000000b012"),
                "team-test",
                uuid("00000000-0000-0000-0000-00000000b022"),
                SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }

    private static SettlementResidentRecord governorResident() {
        return new SettlementResidentRecord(
                uuid("00000000-0000-0000-0000-00000000b003"),
                SettlementResidentRole.GOVERNOR_RECRUIT,
                SettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                SettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                SettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static SettlementSnapshot settlementWithOpenMarkets(SettlementResidentRecord resident, int openMarketCount) {
        return new SettlementSnapshot(
                uuid("00000000-0000-0000-0000-00000000c001"),
                0,
                0,
                null,
                ACTIVE_TIME,
                4,
                4,
                1,
                1,
                0,
                0,
                SettlementStockpileSummary.empty(),
                new SettlementMarketState(Math.max(1, openMarketCount), openMarketCount, 0, 0, 0, 0, List.of(), List.of()),
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                List.of(resident),
                List.of()
        );
    }

    private static SettlementSnapshot settlementWithStockpile(SettlementResidentRecord resident) {
        return new SettlementSnapshot(
                uuid("00000000-0000-0000-0000-00000000c016"),
                0,
                0,
                null,
                ACTIVE_TIME,
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
                        uuid("00000000-0000-0000-0000-00000000f016"),
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

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
