package com.talhanation.bannermod.society;

import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.BannerModGameTestSupport;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementDesiredGoodsSnapshot;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementMarketRecord;
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
import com.talhanation.bannermod.settlement.goal.BannerModResidentGoalScheduler;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentStopReason;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.ResidentTaskOutcome;
import com.talhanation.bannermod.settlement.goal.impl.DefendResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.EatResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.HideResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.IdleResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentRuntime;
import com.talhanation.bannermod.settlement.household.GoHomeResidentGoal;
import com.talhanation.bannermod.settlement.household.HomePreference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public final class NpcSocietyPhaseTwoGameTests {
    private static final long ACTIVE_TIME = 6000L;
    private static final long NIGHT_TIME = 15000L;

    private NpcSocietyPhaseTwoGameTests() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void hungerPressureSelectsEatAndPublishesMarketAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042001");
        UUID marketUuid = UUID.fromString("00000000-0000-0000-0000-000000042011");
        SettlementBuildingRecord market = building(marketUuid, "bannermod:market_stall", helper.absolutePos(new BlockPos(10, 2, 10)), 0);
        SettlementSnapshot snapshot = snapshot(
                ACTIVE_TIME,
                List.of(villagerResident(residentId)),
                List.of(market),
                marketState(marketUuid)
        );
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withNeedState(95, 10, 10, 10, ACTIVE_TIME);

        seedProfile(level, profile);
        SettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(villagerResident(residentId), snapshot, ACTIVE_TIME, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, EatResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcSocietyProfile stored = NpcSocietyAccess.profileFor(level, residentId).orElseThrow();
        helper.assertTrue(stored.currentIntent() == NpcIntent.EAT,
                "Expected hunger pressure to publish EAT intent.");
        helper.assertTrue(stored.currentAnchor() == NpcAnchorType.MARKET,
                "Expected hungry resident without a home to publish MARKET as the current anchor.");
        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("eat".equals(aiSnapshot.aiCurrentGoalLabel()),
                "Expected AI observability to publish the selected eat goal.");
        helper.assertTrue("hunger_pressure".equals(aiSnapshot.aiChoiceReasonTag().toLowerCase())
                        || "severe_hunger".equals(aiSnapshot.aiChoiceReasonTag().toLowerCase()),
                "Expected AI observability to explain EAT via hunger pressure.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void heavyFatigueWithHomeSelectsGoHomeBeforeRest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042002");
        UUID homeUuid = UUID.fromString("00000000-0000-0000-0000-000000042012");
        SettlementBuildingRecord home = building(homeUuid, "bannermod:house", helper.absolutePos(new BlockPos(12, 2, 12)), 4);
        SettlementSnapshot snapshot = snapshot(
                NIGHT_TIME,
                List.of(workerResident(residentId, null, null)),
                List.of(home),
                SettlementMarketState.empty()
        );
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        homeRuntime.assign(residentId, homeUuid, HomePreference.ASSIGNED, NIGHT_TIME);
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                SettlementMarketState::empty,
                new com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime()
        );
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, NIGHT_TIME)
                .withPhaseOneState(null, homeUuid, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE,
                        NpcSocietyDecisionSnapshot.empty(), NIGHT_TIME)
                .withNeedState(10, 95, 10, 10, NIGHT_TIME);

        seedProfile(level, profile);
        SettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(workerResident(residentId, null, null), snapshot, NIGHT_TIME, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, GoHomeResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcSocietyProfile stored = NpcSocietyAccess.profileFor(level, residentId).orElseThrow();
        helper.assertTrue(stored.currentIntent() == NpcIntent.GO_HOME,
                "Expected a heavily fatigued resident with a home to choose GO_HOME first.");
        helper.assertTrue(stored.currentAnchor() == NpcAnchorType.HOME,
                "Expected GO_HOME to publish the home anchor.");
        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("go_home".equals(aiSnapshot.aiCurrentGoalLabel()),
                "Expected AI observability to publish the go-home goal.");
        helper.assertTrue("rest_window".equals(aiSnapshot.aiChoiceReasonTag().toLowerCase())
                        || "fatigue_spike".equals(aiSnapshot.aiChoiceReasonTag().toLowerCase()),
                "Expected AI observability to explain why GO_HOME won.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void socialNeedNowFallsBackToIdleWithoutMarketAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042003");
        UUID marketUuid = UUID.fromString("00000000-0000-0000-0000-000000042013");
        SettlementBuildingRecord market = building(marketUuid, "bannermod:market_stall", helper.absolutePos(new BlockPos(8, 2, 8)), 0);
        SettlementSnapshot snapshot = snapshot(
                ACTIVE_TIME,
                List.of(villagerResident(residentId)),
                List.of(market),
                marketState(marketUuid)
        );
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withNeedState(5, 5, 95, 5, ACTIVE_TIME);

        seedProfile(level, profile);
        SettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(villagerResident(residentId), snapshot, ACTIVE_TIME, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, IdleResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcSocietyProfile stored = NpcSocietyAccess.profileFor(level, residentId).orElseThrow();
        helper.assertTrue(stored.currentIntent() == NpcIntent.IDLE,
                "Expected high social pressure to fall back to the cheap idle intent instead of selecting a dedicated social slice.");
        helper.assertTrue(stored.currentAnchor() == NpcAnchorType.STREET,
                "Expected the cheap idle fallback to avoid publishing a special market social anchor.");
        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("idle".equals(aiSnapshot.aiCurrentGoalLabel()),
                "Expected AI observability to publish the idle fallback once social scheduling is removed.");
        helper.assertTrue("no_higher_priority_goal".equals(aiSnapshot.aiChoiceReasonTag().toLowerCase()),
                "Expected AI observability to explain that no higher-priority goal beat the fallback.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void hungryHomelessResidentWithoutMarketPublishesBlockedEatReason(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042021");
        SettlementSnapshot snapshot = snapshot(
                ACTIVE_TIME,
                List.of(villagerResident(residentId)),
                List.of(),
                SettlementMarketState.empty()
        );
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withNeedState(96, 15, 10, 10, ACTIVE_TIME);

        seedProfile(level, profile);
        SettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(villagerResident(residentId), snapshot, ACTIVE_TIME, profile);
        scheduler.tick(ctx);

        ResidentTask task = scheduler.currentTask(residentId).orElseThrow();
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("eat".equals(aiSnapshot.aiBlockedGoalLabel()),
                "Expected blocked-goal observability to report eat when hunger is high but no food access exists.");
        helper.assertTrue("no_food_access".equals(aiSnapshot.aiBlockedReasonTag().toLowerCase()),
                "Expected blocked-goal observability to explain the denial as missing food access.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void villagerThreatPublishesBlockedDefendReasonWhileHiding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042022");
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withNeedState(5, 5, 5, 92, ACTIVE_TIME);
        ResidentGoalContext ctx = new ResidentGoalContext(villagerResident(residentId), null, ACTIVE_TIME, profile);

        seedProfile(level, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, HideResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, Map.of());

        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("hide".equals(aiSnapshot.aiCurrentGoalLabel()),
                "Expected AI observability to publish the current hide goal for threatened villagers.");
        helper.assertTrue("defend".equals(aiSnapshot.aiBlockedGoalLabel()),
                "Expected AI observability to show defend as the refused alternative for villagers under threat.");
        helper.assertTrue("role_cannot_defend".equals(aiSnapshot.aiBlockedReasonTag().toLowerCase()),
                "Expected AI observability to explain that villagers cannot take the defend path.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void threatPressureHidesVillagersButDefendsGovernors(GameTestHelper helper) {
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-000000042004");
        UUID governorId = UUID.fromString("00000000-0000-0000-0000-000000042005");

        ResidentGoalContext villagerCtx = new ResidentGoalContext(
                villagerResident(villagerId),
                null,
                ACTIVE_TIME,
                NpcSocietyProfile.createDefault(villagerId, ACTIVE_TIME).withNeedState(5, 5, 5, 92, ACTIVE_TIME)
        );
        ResidentGoalContext governorCtx = new ResidentGoalContext(
                governorResident(governorId),
                null,
                ACTIVE_TIME,
                NpcSocietyProfile.createDefault(governorId, ACTIVE_TIME).withNeedState(5, 5, 5, 92, ACTIVE_TIME)
        );

        scheduler.tick(villagerCtx);
        requireTask(helper, scheduler, villagerId, HideResidentGoal.ID.toString());
        scheduler.reset();

        scheduler.tick(governorCtx);
        requireTask(helper, scheduler, governorId, DefendResidentGoal.ID.toString());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void workerLaborPausesWhenSocietyIntentIsNotWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, new BlockPos(2, 1, 2));
        com.talhanation.bannermod.entity.civilian.workarea.CropArea area = BannerModGameTestSupport.spawnOwnedCropArea(helper, owner, new BlockPos(5, 1, 2));
        worker.setFollowState(0);
        worker.setCurrentWorkArea(area);

        NpcSocietyAccess.reconcilePhaseOneState(
                level,
                worker.getUUID(),
                null,
                null,
                worker.getBoundWorkAreaUUID(),
                NpcDailyPhase.ACTIVE,
                NpcIntent.WORK,
                NpcAnchorType.WORKPLACE,
                NpcSocietyDecisionSnapshot.empty(),
                ACTIVE_TIME
        );
        helper.assertTrue(worker.shouldWork(),
                "Expected worker labor to remain enabled while society intent is WORK.");

        NpcSocietyAccess.reconcilePhaseOneState(
                level,
                worker.getUUID(),
                null,
                null,
                worker.getBoundWorkAreaUUID(),
                NpcDailyPhase.ACTIVE,
                NpcIntent.IDLE,
                NpcAnchorType.STREET,
                NpcSocietyDecisionSnapshot.empty(),
                ACTIVE_TIME + 1L
        );
        helper.assertFalse(worker.shouldWork(),
                "Expected non-work society intent to pause worker labor selection.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void assignedMissingBuildingWorkerStillChoosesWorkDuringActivePhase(GameTestHelper helper) {
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042006");
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, ACTIVE_TIME)
                .withNeedState(10, 18, 72, 5, ACTIVE_TIME);
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId, null, null, SettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING),
                null,
                ACTIVE_TIME,
                profile
        );

        scheduler.tick(ctx);

        requireTask(helper, scheduler, residentId, WorkResidentGoal.ID.toString());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void workerFallsBackToIdleDuringLeisureGapAfterShift(GameTestHelper helper) {
        long leisureTime = 10000L;
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042007");
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals();
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, leisureTime)
                .withNeedState(10, 10, 95, 5, leisureTime);
        ResidentGoalContext ctx = new ResidentGoalContext(workerResident(residentId, null, null), null, leisureTime, profile);

        scheduler.tick(ctx);

        requireTask(helper, scheduler, residentId, IdleResidentGoal.ID.toString());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void lateDayHomeboundFallbackPublishesHomeAnchor(GameTestHelper helper) {
        long leisureTime = 11550L;
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042008");
        UUID homeUuid = UUID.fromString("00000000-0000-0000-0000-000000042018");
        SettlementBuildingRecord home = building(homeUuid, "bannermod:house", helper.absolutePos(new BlockPos(12, 2, 12)), 4);
        SettlementSnapshot snapshot = snapshot(
                leisureTime,
                List.of(villagerResident(residentId)),
                List.of(home),
                SettlementMarketState.empty()
        );
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        homeRuntime.assign(residentId, homeUuid, HomePreference.ASSIGNED, leisureTime);
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                SettlementMarketState::empty,
                new com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime()
        );
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, leisureTime)
                .withPhaseOneState(null, homeUuid, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE,
                        NpcSocietyDecisionSnapshot.empty(), leisureTime)
                .withNeedState(10, 10, 95, 5, leisureTime);
        ResidentGoalContext ctx = new ResidentGoalContext(
                villagerResident(residentId),
                snapshot,
                leisureTime,
                leisureTime,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        seedProfile(level, profile);
        SettlementManager.get(level).putSnapshot(snapshot);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, GoHomeResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcSocietyProfile stored = NpcSocietyAccess.profileFor(level, residentId).orElseThrow();
        helper.assertTrue(stored.currentAnchor() == NpcAnchorType.HOME,
                "Expected late-day fallback to publish the home anchor once dedicated leisure social slices are gone.");
        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("soon_night_homebound".equals(aiSnapshot.aiRouteReasonTag().toLowerCase()),
                "Expected observability to explain that evening residents are now simply heading home.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void nightGoHomeChainSettlesIntoRestAfterReturnWindow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042009");
        UUID homeUuid = UUID.fromString("00000000-0000-0000-0000-000000042019");
        SettlementBuildingRecord home = building(homeUuid, "bannermod:house", helper.absolutePos(new BlockPos(12, 2, 12)), 4);
        SettlementSnapshot snapshot = snapshot(
                NIGHT_TIME,
                List.of(workerResident(residentId, null, null)),
                List.of(home),
                SettlementMarketState.empty()
        );
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        homeRuntime.assign(residentId, homeUuid, HomePreference.ASSIGNED, NIGHT_TIME - 200L);
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                SettlementMarketState::empty,
                new com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime()
        );
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, NIGHT_TIME)
                .withPhaseOneState(null, homeUuid, null, NpcDailyPhase.RETURNING_HOME, NpcIntent.GO_HOME, NpcAnchorType.HOME,
                        new NpcSocietyDecisionSnapshot("EXECUTING", GoHomeResidentGoal.ID.toString(), "REST_WINDOW", "SOON_NIGHT_HOMEBOUND", null, "NONE", NpcIntent.WORK.name(), NIGHT_TIME - 120L), NIGHT_TIME)
                .withNeedState(10, 92, 10, 10, NIGHT_TIME);

        seedProfile(level, profile);
        SettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(workerResident(residentId, null, null), snapshot, NIGHT_TIME, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("settling_at_home_for_rest".equals(aiSnapshot.aiRouteReasonTag().toLowerCase()),
                "Expected the return-home chain to publish a clear settle-into-rest route reason once night rest takes over.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void morningLeaveHomeChainFansOutIntoWork(GameTestHelper helper) {
        long morningTick = 1080L;
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042010");
        UUID homeUuid = UUID.fromString("00000000-0000-0000-0000-000000042020");
        SettlementBuildingRecord home = building(homeUuid, "bannermod:house", helper.absolutePos(new BlockPos(6, 2, 6)), 4);
        SettlementSnapshot snapshot = snapshot(
                morningTick,
                List.of(workerResident(residentId, null, null)),
                List.of(home),
                SettlementMarketState.empty()
        );
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        homeRuntime.assign(residentId, homeUuid, HomePreference.ASSIGNED, morningTick - 200L);
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                SettlementMarketState::empty,
                new com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime()
        );
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, morningTick)
                .withPhaseOneState(null, homeUuid, UUID.fromString("00000000-0000-0000-0000-000000042099"), NpcDailyPhase.DEPARTING_HOME, NpcIntent.LEAVE_HOME, NpcAnchorType.STREET,
                        new NpcSocietyDecisionSnapshot("EXECUTING", com.talhanation.bannermod.settlement.household.LeaveHomeResidentGoal.ID.toString(), "EARLY_ACTIVE_WINDOW", "LEAVING_HOME_FOR_WORK", null, "NONE", NpcIntent.REST.name(), morningTick - 70L), morningTick)
                .withNeedState(10, 18, 18, 5, morningTick);

        seedProfile(level, profile);
        SettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(workerResident(residentId, null, null), snapshot, morningTick, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, WorkResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("starting_workday_after_home".equals(aiSnapshot.aiRouteReasonTag().toLowerCase()),
                "Expected the morning bridge goal to resolve into a readable heading-to-work route once the worker has stepped out of the house.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void invalidatedWorkRecoveryPublishesReadableHomeRegroup(GameTestHelper helper) {
        long gameTime = 9200L;
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042011");
        UUID homeUuid = UUID.fromString("00000000-0000-0000-0000-000000042031");
        SettlementBuildingRecord home = building(homeUuid, "bannermod:house", helper.absolutePos(new BlockPos(8, 2, 8)), 4);
        SettlementSnapshot snapshot = snapshot(
                gameTime,
                List.of(workerResident(residentId, null, null)),
                List.of(home),
                SettlementMarketState.empty()
        );
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        homeRuntime.assign(residentId, homeUuid, HomePreference.ASSIGNED, gameTime - 120L);
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, gameTime)
                .withPhaseOneState(null, homeUuid, UUID.fromString("00000000-0000-0000-0000-000000042099"), NpcDailyPhase.ACTIVE,
                        NpcIntent.WORK, NpcAnchorType.WORKPLACE,
                        new NpcSocietyDecisionSnapshot("BLOCKED", null, "ASSIGNED_SHIFT", "HEADING_TO_WORKPLACE",
                                WorkResidentGoal.ID.toString(), NpcSocietyDecisionSnapshot.BLOCKED_REASON_CONTEXT_INVALIDATED,
                                NpcIntent.WORK.name(), gameTime - 40L),
                        gameTime)
                .withNeedState(16, 56, 18, 18, gameTime);
        ResidentGoalContext ctx = new ResidentGoalContext(
                workerResident(residentId, null, null),
                snapshot,
                gameTime,
                gameTime,
                profile,
                4,
                NpcHouseholdHousingState.NORMAL,
                true,
                2
        );

        seedProfile(level, profile);
        SettlementManager.get(level).putSnapshot(snapshot);

        NpcSocietyPhaseOneRuntime.updateResidentProfile(
                level,
                homeRuntime,
                ctx,
                new ResidentTask(GoHomeResidentGoal.ID, gameTime, 40),
                new ResidentTaskOutcome(WorkResidentGoal.ID, ResidentStopReason.CONTEXT_INVALID, gameTime - 20L),
                byBuilding(snapshot)
        );

        NpcPhaseOneSnapshot aiSnapshot = NpcSocietyAccess.phaseOneSnapshot(level, residentId, null);
        helper.assertTrue("RECOVERING".equals(aiSnapshot.aiStateTag()),
                "Expected a broken work route to publish the recovering AI state.");
        helper.assertTrue("work".equals(aiSnapshot.aiBlockedGoalLabel()),
                "Expected observability to remember which work goal just broke.");
        helper.assertTrue("context_invalidated".equals(aiSnapshot.aiBlockedReasonTag().toLowerCase()),
                "Expected observability to distinguish an invalidated context from a generic timeout.");
        helper.assertTrue("regrouping_at_home".equals(aiSnapshot.aiRouteReasonTag().toLowerCase()),
                "Expected recovery routing to explain that the worker is regrouping at home after the broken path.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 160)
    public static void idleIntentDoesNotChaseSquareSpotWithoutMarket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CitizenEntity citizen = BannerModGameTestSupport.spawnEntity(helper, ModCitizenEntityTypes.CITIZEN.get(), new BlockPos(1, 1, 1));
        UUID squareUuid = UUID.fromString("00000000-0000-0000-0000-000000042021");
        BlockPos squarePos = helper.absolutePos(new BlockPos(12, 1, 1));
        SettlementBuildingRecord square = building(squareUuid, "bannermod:village_square", squarePos, 0);
        SettlementSnapshot snapshot = snapshot(
                ACTIVE_TIME,
                List.of(villagerResident(citizen.getUUID())),
                List.of(square),
                SettlementMarketState.empty()
        );

        SettlementManager.get(level).putSnapshot(snapshot);
        NpcSocietyAccess.reconcilePhaseOneState(
                level,
                citizen.getUUID(),
                null,
                null,
                null,
                NpcDailyPhase.ACTIVE,
                NpcIntent.IDLE,
                NpcAnchorType.STREET,
                NpcSocietyDecisionSnapshot.empty(),
                ACTIVE_TIME
        );
        double startDistance = citizen.distanceToSqr(Vec3.atCenterOf(squarePos));

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(
                    citizen.distanceToSqr(Vec3.atCenterOf(squarePos)) >= startDistance - 4.0D,
                    "Expected idle intent to stop chasing named social gathering anchors."
            );
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 120)
    public static void idleIntentDoesNotChaseSettlementAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CitizenEntity citizen = BannerModGameTestSupport.spawnEntity(helper, ModCitizenEntityTypes.CITIZEN.get(), new BlockPos(1, 1, 1));
        UUID marketUuid = UUID.fromString("00000000-0000-0000-0000-000000042016");
        BlockPos marketPos = helper.absolutePos(new BlockPos(6, 1, 1));
        SettlementBuildingRecord market = building(marketUuid, "bannermod:market_stall", marketPos, 0);
        SettlementSnapshot snapshot = snapshot(
                ACTIVE_TIME,
                List.of(villagerResident(citizen.getUUID())),
                List.of(market),
                marketState(marketUuid)
        );

        SettlementManager.get(level).putSnapshot(snapshot);
        NpcSocietyAccess.reconcilePhaseOneState(
                level,
                citizen.getUUID(),
                null,
                null,
                null,
                NpcDailyPhase.ACTIVE,
                NpcIntent.IDLE,
                NpcAnchorType.STREET,
                NpcSocietyDecisionSnapshot.empty(),
                ACTIVE_TIME
        );
        double startDistance = citizen.distanceToSqr(Vec3.atCenterOf(marketPos));

        helper.runAfterDelay(20, () -> {
            NpcSocietyProfile stored = NpcSocietyAccess.profileFor(level, citizen.getUUID()).orElseThrow();
            helper.assertTrue(stored.currentIntent() == NpcIntent.IDLE,
                    "Expected the citizen to stay on the cheap idle intent when no other coarse goal wins.");
            helper.assertTrue(stored.currentAnchor() == NpcAnchorType.STREET,
                    "Expected idle intent to avoid keeping a dedicated market social anchor.");
            helper.assertTrue(citizen.distanceToSqr(Vec3.atCenterOf(marketPos)) >= startDistance - 4.0D,
                    "Expected idle intent not to pull the citizen toward the market anchor.");
            helper.succeed();
        });
    }

    private static ResidentTask requireTask(GameTestHelper helper,
                                            BannerModResidentGoalScheduler scheduler,
                                            UUID residentId,
                                            String expectedGoalId) {
        Optional<ResidentTask> task = scheduler.currentTask(residentId);
        helper.assertTrue(task.isPresent(), "Expected resident scheduler to publish a task.");
        helper.assertTrue(expectedGoalId.equals(task.get().goalId().toString()),
                "Expected task " + expectedGoalId + " but got " + task.get().goalId() + ".");
        return task.get();
    }

    private static void seedProfile(ServerLevel level, NpcSocietyProfile profile) {
        NpcSocietySavedData.get(level).runtime().seedResident(profile);
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

    private static SettlementResidentRecord workerResident(UUID residentId, UUID ownerUuid, String teamId) {
        return workerResident(residentId, ownerUuid, teamId, SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING);
    }

    private static SettlementResidentRecord workerResident(UUID residentId,
                                                           UUID ownerUuid,
                                                           String teamId,
                                                           SettlementResidentAssignmentState assignmentState) {
        UUID workAreaUuid = UUID.fromString("00000000-0000-0000-0000-000000042099");
        return new SettlementResidentRecord(
                residentId,
                SettlementResidentRole.CONTROLLED_WORKER,
                SettlementResidentScheduleSeed.ASSIGNED_WORK,
                SettlementResidentScheduleWindowSeed.defaultFor(
                        SettlementResidentScheduleSeed.ASSIGNED_WORK,
                        SettlementResidentRuntimeRoleState.LOCAL_LABOR
                ),
                SettlementResidentRuntimeRoleState.LOCAL_LABOR,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                ownerUuid,
                teamId,
                workAreaUuid,
                assignmentState
        );
    }

    private static SettlementResidentRecord governorResident(UUID residentId) {
        return new SettlementResidentRecord(
                residentId,
                SettlementResidentRole.GOVERNOR_RECRUIT,
                SettlementResidentScheduleSeed.GOVERNING,
                SettlementResidentScheduleWindowSeed.CIVIC_DAY,
                SettlementResidentRuntimeRoleState.GOVERNANCE,
                SettlementResidentServiceContract.notServiceActor(),
                SettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                SettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static SettlementBuildingRecord building(UUID buildingUuid, String typeId, BlockPos originPos, int residentCapacity) {
        return new SettlementBuildingRecord(
                buildingUuid,
                typeId,
                originPos,
                null,
                null,
                residentCapacity,
                0,
                0,
                List.of()
        );
    }

    private static SettlementSnapshot snapshot(long gameTime,
                                               List<SettlementResidentRecord> residents,
                                               List<SettlementBuildingRecord> buildings,
                                               SettlementMarketState marketState) {
        return new SettlementSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000042777"),
                0,
                0,
                null,
                gameTime,
                0,
                0,
                0,
                residents.size(),
                0,
                0,
                SettlementStockpileSummary.empty(),
                marketState,
                SettlementDesiredGoodsSnapshot.empty(),
                SettlementProjectCandidateSnapshot.empty(),
                SettlementTradeRouteHandoffSnapshot.empty(),
                SettlementSupplySignalState.empty(),
                residents,
                buildings
        );
    }

    private static SettlementMarketState marketState(UUID marketBuildingUuid) {
        return new SettlementMarketState(
                1,
                1,
                16,
                8,
                0,
                0,
                List.of(new SettlementMarketRecord(marketBuildingUuid, "market", true, 16, 8)),
                List.of()
        );
    }

    private static Map<UUID, SettlementBuildingRecord> byBuilding(SettlementSnapshot snapshot) {
        Map<UUID, SettlementBuildingRecord> indexed = new LinkedHashMap<>();
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && building.buildingUuid() != null) {
                indexed.put(building.buildingUuid(), building);
            }
        }
        return indexed;
    }
}
