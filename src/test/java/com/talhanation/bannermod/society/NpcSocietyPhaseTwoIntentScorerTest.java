package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodsSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketState;
import com.talhanation.bannermod.settlement.BannerModSettlementProjectCandidateSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentMode;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRole;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRuntimeRoleSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentServiceContract;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import com.talhanation.bannermod.settlement.BannerModSettlementStockpileSummary;
import com.talhanation.bannermod.settlement.BannerModSettlementSupplySignalState;
import com.talhanation.bannermod.settlement.BannerModSettlementTradeRouteHandoffSeed;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SocialiseResidentGoal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSocietyPhaseTwoIntentScorerTest {
    private static final long ACTIVE_TIME = 6000L;
    private static final long REST_TIME = 15000L;

    @Test
    void fearWeightedHideOutranksRestDuringActivePhase() {
        ResidentGoalContext ctx = context(
                villagerResident(),
                ACTIVE_TIME,
                null,
                NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a001"), ACTIVE_TIME)
                        .withNeedState(5, 5, 5, 12, ACTIVE_TIME)
                        .withSocialState(50, 42, 0, 0, 50, ACTIVE_TIME)
        );

        int hide = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.HIDE);
        int rest = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.REST);

        assertEquals(80, hide, "hide score should reflect danger + fear weighting exactly");
        assertEquals(7, rest, "rest should only receive the small residual fear term during active phase");
        assertTrue(hide > rest, "high fear during active phase must push villagers toward hiding over resting");
    }

    @Test
    void restGoalBasePriorityIsOnlyAppliedInRestPhase() {
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a002"), ACTIVE_TIME)
                .withNeedState(5, 5, 5, 12, ACTIVE_TIME)
                .withSocialState(50, 42, 0, 0, 50, ACTIVE_TIME);
        RestResidentGoal goal = new RestResidentGoal();

        int activePriority = goal.computePriority(context(villagerResident(), ACTIVE_TIME, null, profile));
        int restPriority = goal.computePriority(context(villagerResident(), REST_TIME, null, profile));

        assertEquals(7, activePriority, "rest goal should not keep an always-on high base priority during active time");
        assertEquals(94, restPriority, "rest phase should still receive the intended overnight base priority");
        assertTrue(restPriority > activePriority, "rest priority must jump sharply once the resident is in rest phase");
    }

    @Test
    void adolescentSocialiseGetsExactBonusWeight() {
        UUID adultId = uuid("00000000-0000-0000-0000-00000000a003");
        UUID adolescentId = uuid("00000000-0000-0000-0000-00000000a004");
        ResidentGoalContext adult = context(
                villagerResident(),
                ACTIVE_TIME,
                null,
                NpcSocietyProfile.createSeeded(adultId, NpcLifeStage.ADULT, NpcSex.MALE, ACTIVE_TIME)
                        .withNeedState(10, 10, 60, 0, ACTIVE_TIME)
                        .withSocialState(50, 0, 0, 0, 50, ACTIVE_TIME)
        );
        ResidentGoalContext adolescent = context(
                villagerResident(adolescentId),
                ACTIVE_TIME,
                null,
                NpcSocietyProfile.createSeeded(adolescentId, NpcLifeStage.ADOLESCENT, NpcSex.MALE, ACTIVE_TIME)
                        .withNeedState(10, 10, 60, 0, ACTIVE_TIME)
                        .withSocialState(50, 0, 0, 0, 50, ACTIVE_TIME)
        );

        int adultScore = NpcSocietyPhaseTwoIntentScorer.scoreIntent(adult, NpcIntent.SOCIALISE);
        int adolescentScore = NpcSocietyPhaseTwoIntentScorer.scoreIntent(adolescent, NpcIntent.SOCIALISE);

        assertEquals(74, adultScore);
        assertEquals(82, adolescentScore);
        assertEquals(8, adolescentScore - adultScore,
                "adolescent socialise weight should add the exact +8 bonus defined by the scorer");
    }

    @Test
    void foodAccessEnablesEatAndSevereHungerBeatsWork() {
        BannerModSettlementResidentRecord worker = workerResident();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a005"), ACTIVE_TIME)
                .withNeedState(92, 10, 10, 10, ACTIVE_TIME)
                .withSocialState(50, 0, 0, 0, 50, ACTIVE_TIME);

        ResidentGoalContext noMarket = context(worker, ACTIVE_TIME, settlementWithOpenMarkets(worker, 0), profile);
        ResidentGoalContext openMarket = context(worker, ACTIVE_TIME, settlementWithOpenMarkets(worker, 1), profile);

        int eatWithoutMarket = NpcSocietyPhaseTwoIntentScorer.scoreIntent(noMarket, NpcIntent.EAT);
        int eatWithMarket = NpcSocietyPhaseTwoIntentScorer.scoreIntent(openMarket, NpcIntent.EAT);
        int workWithMarket = NpcSocietyPhaseTwoIntentScorer.scoreIntent(openMarket, NpcIntent.WORK);

        assertEquals(0, eatWithoutMarket, "eat should stay unavailable when the resident has no home and no market access");
        assertEquals(114, eatWithMarket, "severe hunger with food access should produce the exact eat pressure from the scorer");
        assertEquals(39, workWithMarket, "the same context should heavily penalize work under severe hunger");
        assertTrue(eatWithMarket > workWithMarket, "severe hunger should out-rank work once food is reachable");
    }

    @Test
    void governorAngerWeightLetsDefendBeatHide() {
        ResidentGoalContext ctx = context(
                governorResident(),
                ACTIVE_TIME,
                null,
                NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a006"), ACTIVE_TIME)
                        .withNeedState(10, 10, 10, 40, ACTIVE_TIME)
                        .withSocialState(50, 30, 80, 0, 60, ACTIVE_TIME)
        );

        int hide = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.HIDE);
        int defend = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.DEFEND);

        assertEquals(0, hide, "hide should be suppressed entirely when a defender's anger clearly exceeds fear");
        assertEquals(120, defend, "defend should clamp after the anger and loyalty weights push it over the cap");
        assertTrue(defend > hide, "armed governor recruits should defend rather than hide when anger dominates fear");
    }

    @Test
    void familyPressureMakesGoHomeStrongerForSettledResidents() {
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a007");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d007");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withPhaseOneState(null, homeId, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE,
                        NpcSocietyDecisionSnapshot.empty(), ACTIVE_TIME)
                .withNeedState(10, 76, 15, 28, ACTIVE_TIME)
                .withSocialState(50, 22, 0, 0, 50, ACTIVE_TIME);

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

        assertTrue(familyScore > aloneScore,
                "family-linked residents should feel a stronger pull toward home under the same pressure");
    }

    @Test
    void fearfulMemoryMakesWorkLessAttractiveThanItWasBefore() {
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a008");
        NpcSocietyProfile calmProfile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withNeedState(18, 12, 18, 18, ACTIVE_TIME)
                .withSocialState(50, 10, 0, 0, 55, ACTIVE_TIME);
        NpcSocietyProfile fearfulProfile = calmProfile.withSocialState(28, 78, 24, 0, 42, ACTIVE_TIME);

        int calmWork = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(workerResident(), ACTIVE_TIME, null, calmProfile), NpcIntent.WORK);
        int fearfulWork = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(workerResident(), ACTIVE_TIME, null, fearfulProfile), NpcIntent.WORK);
        int fearfulHide = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(workerResident(), ACTIVE_TIME, null, fearfulProfile), NpcIntent.HIDE);

        assertTrue(fearfulWork < calmWork,
                "fear-heavy memory should suppress normal work behavior");
        assertTrue(fearfulHide > fearfulWork,
                "fear-heavy memory should produce a visible safety behavior instead of routine labor");
    }

    @Test
    void leisurePhaseLetsWorkersSocialiseAfterTheirShift() {
        long leisureTime = 10000L;
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(uuid("00000000-0000-0000-0000-00000000a009"), leisureTime)
                .withNeedState(10, 12, 85, 6, leisureTime)
                .withSocialState(50, 0, 0, 0, 55, leisureTime);

        ResidentGoalContext ctx = context(workerResident(), leisureTime, null, profile);
        int work = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.WORK);
        int socialise = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.SOCIALISE);

        assertEquals(0, work, "work should stay off once the labor window closes");
        assertTrue(socialise > 0, "socialise should stay available in the evening leisure gap");
        assertTrue(socialise > work, "post-shift leisure should produce readable social behavior instead of idle drift");
    }

    @Test
    void eveningWindowStrengthensGoHomePressureBeforeRest() {
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a010");
        UUID homeId = uuid("00000000-0000-0000-0000-00000000d010");
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withPhaseOneState(null, homeId, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE,
                        NpcSocietyDecisionSnapshot.empty(), ACTIVE_TIME)
                .withNeedState(10, 20, 20, 10, ACTIVE_TIME)
                .withSocialState(50, 0, 0, 0, 50, ACTIVE_TIME);

        int middayScore = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(villagerResident(residentId), ACTIVE_TIME, null, profile), NpcIntent.GO_HOME);
        int eveningScore = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(villagerResident(residentId), 11550L, null, profile), NpcIntent.GO_HOME);

        assertTrue(eveningScore > middayScore,
                "go-home pressure should rise as the rest window approaches so NPCs start pulling back toward home");
    }

    @Test
    void shortIntentHistoryKeepsSocialiseMoreStable() {
        long time = 10000L;
        UUID residentId = uuid("00000000-0000-0000-0000-00000000a011");
        NpcSocietyProfile neutralProfile = NpcSocietyProfile.createDefault(residentId, time)
                .withNeedState(10, 10, 75, 8, time)
                .withSocialState(50, 0, 0, 0, 50, time);
        NpcSocietyProfile stickyProfile = neutralProfile.withPhaseOneState(
                null,
                null,
                null,
                NpcDailyPhase.ACTIVE,
                NpcIntent.SOCIALISE,
                NpcAnchorType.STREET,
                new NpcSocietyDecisionSnapshot("EXECUTING", SocialiseResidentGoal.ID.toString(), "SOCIAL_PRESSURE", "STREET_SIDE_CHAT", null, "NONE", NpcIntent.WORK.name(), time - 40L),
                time
        );

        int neutral = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(villagerResident(residentId), time, null, neutralProfile), NpcIntent.SOCIALISE);
        int sticky = NpcSocietyPhaseTwoIntentScorer.scoreIntent(context(villagerResident(residentId), time, null, stickyProfile), NpcIntent.SOCIALISE);

        assertTrue(sticky > neutral,
                "recently selected social intent should receive a small history bonus so the NPC does not oscillate on near-tied routine choices");
    }

    private static ResidentGoalContext context(BannerModSettlementResidentRecord resident,
                                               long gameTime,
                                               BannerModSettlementSnapshot settlement,
                                               NpcSocietyProfile profile) {
        return new ResidentGoalContext(resident, settlement, gameTime, profile);
    }

    private static BannerModSettlementResidentRecord villagerResident() {
        return villagerResident(uuid("00000000-0000-0000-0000-00000000b001"));
    }

    private static BannerModSettlementResidentRecord villagerResident(UUID residentId) {
        return new BannerModSettlementResidentRecord(
                residentId,
                BannerModSettlementResidentRole.VILLAGER,
                BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                BannerModSettlementResidentRuntimeRoleSeed.VILLAGE_LIFE,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static BannerModSettlementResidentRecord workerResident() {
        return new BannerModSettlementResidentRecord(
                uuid("00000000-0000-0000-0000-00000000b002"),
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY,
                BannerModSettlementResidentRuntimeRoleSeed.LOCAL_LABOR,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                uuid("00000000-0000-0000-0000-00000000b012"),
                "team-test",
                uuid("00000000-0000-0000-0000-00000000b022"),
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }

    private static BannerModSettlementResidentRecord governorResident() {
        return new BannerModSettlementResidentRecord(
                uuid("00000000-0000-0000-0000-00000000b003"),
                BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                BannerModSettlementResidentRuntimeRoleSeed.VILLAGE_LIFE,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static BannerModSettlementSnapshot settlementWithOpenMarkets(BannerModSettlementResidentRecord resident, int openMarketCount) {
        return new BannerModSettlementSnapshot(
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
                BannerModSettlementStockpileSummary.empty(),
                new BannerModSettlementMarketState(Math.max(1, openMarketCount), openMarketCount, 0, 0, 0, 0, List.of(), List.of()),
                BannerModSettlementDesiredGoodsSeed.empty(),
                BannerModSettlementProjectCandidateSeed.empty(),
                BannerModSettlementTradeRouteHandoffSeed.empty(),
                BannerModSettlementSupplySignalState.empty(),
                List.of(resident),
                List.of()
        );
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
