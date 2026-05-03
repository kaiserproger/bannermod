package com.talhanation.bannermod.society;

import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.BannerModGameTestSupport;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.registry.citizen.ModCitizenEntityTypes;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementDesiredGoodsSeed;
import com.talhanation.bannermod.settlement.BannerModSettlementManager;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketRecord;
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
import com.talhanation.bannermod.settlement.goal.BannerModResidentGoalScheduler;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.DefendResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.EatResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.HideResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SocialiseResidentGoal;
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
        BannerModSettlementBuildingRecord market = building(marketUuid, "bannermod:market_stall", helper.absolutePos(new BlockPos(10, 2, 10)), 0);
        BannerModSettlementSnapshot snapshot = snapshot(
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
        BannerModSettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(villagerResident(residentId), snapshot, ACTIVE_TIME, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, EatResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcSocietyProfile stored = NpcSocietyAccess.profileFor(level, residentId).orElseThrow();
        helper.assertTrue(stored.currentIntent() == NpcIntent.EAT,
                "Expected hunger pressure to publish EAT intent.");
        helper.assertTrue(stored.currentAnchor() == NpcAnchorType.MARKET,
                "Expected hungry resident without a home to publish MARKET as the current anchor.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void heavyFatigueWithHomeSelectsGoHomeBeforeRest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042002");
        UUID homeUuid = UUID.fromString("00000000-0000-0000-0000-000000042012");
        BannerModSettlementBuildingRecord home = building(homeUuid, "bannermod:house", helper.absolutePos(new BlockPos(12, 2, 12)), 4);
        BannerModSettlementSnapshot snapshot = snapshot(
                NIGHT_TIME,
                List.of(workerResident(residentId, null, null)),
                List.of(home),
                BannerModSettlementMarketState.empty()
        );
        BannerModHomeAssignmentRuntime homeRuntime = new BannerModHomeAssignmentRuntime();
        homeRuntime.assign(residentId, homeUuid, HomePreference.ASSIGNED, NIGHT_TIME);
        BannerModResidentGoalScheduler scheduler = BannerModResidentGoalScheduler.withDefaultGoals(
                homeRuntime,
                BannerModSettlementMarketState::empty,
                new com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime()
        );
        NpcSocietyProfile profile = NpcSocietyProfile.createDefault(residentId, NIGHT_TIME)
                .withPhaseOneState(null, homeUuid, null, NpcDailyPhase.ACTIVE, NpcIntent.UNSPECIFIED, NpcAnchorType.NONE, NIGHT_TIME)
                .withNeedState(10, 95, 10, 10, NIGHT_TIME);

        seedProfile(level, profile);
        BannerModSettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(workerResident(residentId, null, null), snapshot, NIGHT_TIME, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, GoHomeResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcSocietyProfile stored = NpcSocietyAccess.profileFor(level, residentId).orElseThrow();
        helper.assertTrue(stored.currentIntent() == NpcIntent.GO_HOME,
                "Expected a heavily fatigued resident with a home to choose GO_HOME first.");
        helper.assertTrue(stored.currentAnchor() == NpcAnchorType.HOME,
                "Expected GO_HOME to publish the home anchor.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void socialNeedSelectsSocialiseAndPublishesMarketAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID residentId = UUID.fromString("00000000-0000-0000-0000-000000042003");
        UUID marketUuid = UUID.fromString("00000000-0000-0000-0000-000000042013");
        BannerModSettlementBuildingRecord market = building(marketUuid, "bannermod:market_stall", helper.absolutePos(new BlockPos(8, 2, 8)), 0);
        BannerModSettlementSnapshot snapshot = snapshot(
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
        BannerModSettlementManager.get(level).putSnapshot(snapshot);

        ResidentGoalContext ctx = new ResidentGoalContext(villagerResident(residentId), snapshot, ACTIVE_TIME, profile);
        scheduler.tick(ctx);

        ResidentTask task = requireTask(helper, scheduler, residentId, SocialiseResidentGoal.ID.toString());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(level, homeRuntime, ctx, task, byBuilding(snapshot));

        NpcSocietyProfile stored = NpcSocietyAccess.profileFor(level, residentId).orElseThrow();
        helper.assertTrue(stored.currentIntent() == NpcIntent.SOCIALISE,
                "Expected strong social pressure to select SOCIALISE.");
        helper.assertTrue(stored.currentAnchor() == NpcAnchorType.MARKET,
                "Expected socialise to publish MARKET when an open market exists.");
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
                NpcIntent.SOCIALISE,
                NpcAnchorType.MARKET,
                ACTIVE_TIME + 1L
        );
        helper.assertFalse(worker.shouldWork(),
                "Expected non-work society intent to pause worker labor selection.");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 160)
    public static void citizenSocialIntentMovesTowardSettlementAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CitizenEntity citizen = BannerModGameTestSupport.spawnEntity(helper, ModCitizenEntityTypes.CITIZEN.get(), new BlockPos(1, 1, 1));
        UUID marketUuid = UUID.fromString("00000000-0000-0000-0000-000000042016");
        BlockPos marketPos = helper.absolutePos(new BlockPos(12, 1, 1));
        BannerModSettlementBuildingRecord market = building(marketUuid, "bannermod:market_stall", marketPos, 0);
        BannerModSettlementSnapshot snapshot = snapshot(
                ACTIVE_TIME,
                List.of(villagerResident(citizen.getUUID())),
                List.of(market),
                marketState(marketUuid)
        );

        BannerModSettlementManager.get(level).putSnapshot(snapshot);
        NpcSocietyAccess.reconcilePhaseOneState(
                level,
                citizen.getUUID(),
                null,
                null,
                null,
                NpcDailyPhase.ACTIVE,
                NpcIntent.SOCIALISE,
                NpcAnchorType.MARKET,
                ACTIVE_TIME
        );
        double startDistance = citizen.distanceToSqr(Vec3.atCenterOf(marketPos));

        helper.succeedWhen(() -> helper.assertTrue(
                citizen.distanceToSqr(Vec3.atCenterOf(marketPos)) < startDistance - 9.0D,
                "Expected social anchor execution to move the citizen closer to the market anchor."
        ));
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

    private static BannerModSettlementResidentRecord workerResident(UUID residentId, UUID ownerUuid, String teamId) {
        UUID workAreaUuid = UUID.fromString("00000000-0000-0000-0000-000000042099");
        return new BannerModSettlementResidentRecord(
                residentId,
                BannerModSettlementResidentRole.CONTROLLED_WORKER,
                BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                BannerModSettlementResidentScheduleWindowSeed.defaultFor(
                        BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK,
                        BannerModSettlementResidentRuntimeRoleSeed.LOCAL_LABOR
                ),
                BannerModSettlementResidentRuntimeRoleSeed.LOCAL_LABOR,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER,
                ownerUuid,
                teamId,
                workAreaUuid,
                BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
        );
    }

    private static BannerModSettlementResidentRecord governorResident(UUID residentId) {
        return new BannerModSettlementResidentRecord(
                residentId,
                BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                BannerModSettlementResidentScheduleSeed.GOVERNING,
                BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY,
                BannerModSettlementResidentRuntimeRoleSeed.GOVERNANCE,
                BannerModSettlementResidentServiceContract.notServiceActor(),
                BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                null,
                null,
                null,
                BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
        );
    }

    private static BannerModSettlementBuildingRecord building(UUID buildingUuid, String typeId, BlockPos originPos, int residentCapacity) {
        return new BannerModSettlementBuildingRecord(
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

    private static BannerModSettlementSnapshot snapshot(long gameTime,
                                                        List<BannerModSettlementResidentRecord> residents,
                                                        List<BannerModSettlementBuildingRecord> buildings,
                                                        BannerModSettlementMarketState marketState) {
        return new BannerModSettlementSnapshot(
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
                BannerModSettlementStockpileSummary.empty(),
                marketState,
                BannerModSettlementDesiredGoodsSeed.empty(),
                BannerModSettlementProjectCandidateSeed.empty(),
                BannerModSettlementTradeRouteHandoffSeed.empty(),
                BannerModSettlementSupplySignalState.empty(),
                residents,
                buildings
        );
    }

    private static BannerModSettlementMarketState marketState(UUID marketBuildingUuid) {
        return new BannerModSettlementMarketState(
                1,
                1,
                16,
                8,
                0,
                0,
                List.of(new BannerModSettlementMarketRecord(marketBuildingUuid, "market", true, 16, 8)),
                List.of()
        );
    }

    private static Map<UUID, BannerModSettlementBuildingRecord> byBuilding(BannerModSettlementSnapshot snapshot) {
        Map<UUID, BannerModSettlementBuildingRecord> indexed = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && building.buildingUuid() != null) {
                indexed.put(building.buildingUuid(), building);
            }
        }
        return indexed;
    }
}
