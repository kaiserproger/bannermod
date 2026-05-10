package com.talhanation.bannermod;

import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.events.WorkersVillagerEvents;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.FishermanEntity;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.entity.civilian.workarea.FishingArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.registry.civilian.ModEntityTypes;
import com.talhanation.bannermod.settlement.civilian.WorkerSettlementSpawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModClaimWorkerGrowthGameTests {

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void friendlyClaimGrowthSpawnsOwnedWorkerWithFactionDefaults(GameTestHelper helper) {
        WorkerSettlementSpawnRules.ClaimGrowthConfig config = claimGrowthConfig(20L, 4);

        ServerLevel level = helper.getLevel();
        String teamId = "phase31_claim_friendly_growth";
        ServerPlayer leader = createLeader(helper, level, UUID.fromString("00000000-0000-0000-0000-000000003111"), "phase31-friendly-leader", teamId);
        BlockPos claimPos = helper.absolutePos(new BlockPos(2, 2, 2));
        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(level, claimPos, teamId, leader.getUUID(), leader.getScoreboardName());
        int baselineWorkers = getClaimWorkers(level, claim).size();

        AbstractWorkerEntity worker = WorkersVillagerEvents.attemptClaimWorkerGrowth(level, claim, teamId, 20L, config);
        List<AbstractWorkerEntity> claimWorkers = getClaimWorkers(level, claim);

        helper.assertTrue(worker != null, "Expected friendly claim growth to spawn one worker when the claim starts empty.");
        helper.assertTrue(claimWorkers.size() == baselineWorkers + 1, "Expected claim growth to add exactly one owned worker to the claim.");
        helper.assertTrue(claimWorkers.stream().anyMatch(grownWorker -> leader.getUUID().equals(grownWorker.getOwnerUUID())),
                "Expected a grown worker to inherit the claim leader owner UUID.");
        helper.assertTrue(claimWorkers.stream().anyMatch(grownWorker -> grownWorker.getTeam() != null && teamId.equals(grownWorker.getTeam().getName())),
                "Expected a grown worker to join the claim leader's faction team.");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void claimGrowthWaitsForDiminishingCooldownBeforeSecondWorker(GameTestHelper helper) {
        WorkerSettlementSpawnRules.ClaimGrowthConfig config = claimGrowthConfig(20L, 4);

        ServerLevel level = helper.getLevel();
        String teamId = "phase31_claim_friendly_cooldown";
        ServerPlayer leader = createLeader(helper, level, UUID.fromString("00000000-0000-0000-0000-000000003112"), "phase31-cooldown-leader", teamId);
        BlockPos claimPos = helper.absolutePos(new BlockPos(2, 2, 2));
        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(level, claimPos, teamId, leader.getUUID(), leader.getScoreboardName());
        int baselineWorkers = getClaimWorkers(level, claim).size();

        AbstractWorkerEntity firstWorker = WorkersVillagerEvents.attemptClaimWorkerGrowth(level, claim, teamId, 100L, config);
        int workersAfterFirst = getClaimWorkers(level, claim).size();
        AbstractWorkerEntity earlyWorker = WorkersVillagerEvents.attemptClaimWorkerGrowth(level, claim, teamId, 139L, config);
        int workersAfterEarly = getClaimWorkers(level, claim).size();
        AbstractWorkerEntity secondWorker = WorkersVillagerEvents.attemptClaimWorkerGrowth(level, claim, teamId, 140L, config);
        List<AbstractWorkerEntity> claimWorkers = getClaimWorkers(level, claim);

        helper.assertTrue(firstWorker != null || workersAfterFirst > baselineWorkers, "Expected the first claim growth attempt to create one worker.");
        helper.assertTrue(workersAfterFirst == baselineWorkers + 1, "Expected the first claim growth attempt to add exactly one worker to the claim.");
        helper.assertTrue(earlyWorker == null, "Expected the second worker to stay blocked until the diminishing cooldown fully expires.");
        helper.assertTrue(workersAfterEarly == workersAfterFirst, "Expected the claim worker count to stay unchanged while cooldown is still active.");
        helper.assertTrue(secondWorker != null || claimWorkers.size() > workersAfterFirst, "Expected cooldown expiry to allow one more worker into the claim.");
        helper.assertTrue(claimWorkers.size() == baselineWorkers + 2, "Expected cooldown expiry to add one more worker to the claim.");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void hostileOrUnclaimedTerritoryNeverSpawnsClaimWorkers(GameTestHelper helper) {
        WorkerSettlementSpawnRules.ClaimGrowthConfig config = claimGrowthConfig(20L, 4);

        ServerLevel level = helper.getLevel();
        String friendlyTeamId = "phase31_claim_friendly_hostile";
        String hostileTeamId = "phase31_claim_hostile_hostile";
        ServerPlayer leader = createLeader(helper, level, UUID.fromString("00000000-0000-0000-0000-000000003113"), "phase31-hostile-leader", friendlyTeamId);
        createLeader(helper, level, UUID.fromString("00000000-0000-0000-0000-000000003114"), "phase31-hostile-player", hostileTeamId);
        BlockPos claimPos = helper.absolutePos(new BlockPos(2, 2, 2));
        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(level, claimPos, friendlyTeamId, leader.getUUID(), leader.getScoreboardName());

        AbstractWorkerEntity hostileWorker = WorkersVillagerEvents.attemptClaimWorkerGrowth(
                level,
                claim,
                new BannerModSettlementBinding.Binding(BannerModSettlementBinding.Status.HOSTILE_CLAIM, hostileTeamId, friendlyTeamId),
                200L,
                config
        );
        int baselineWorkers = getClaimWorkers(level, claim).size();
        AbstractWorkerEntity unclaimedWorker = WorkersVillagerEvents.attemptClaimWorkerGrowth(
                level,
                null,
                new BannerModSettlementBinding.Binding(BannerModSettlementBinding.Status.UNCLAIMED, friendlyTeamId, null),
                200L,
                config
        );

        helper.assertTrue(hostileWorker == null, "Expected hostile claim territory to deny claim worker growth.");
        helper.assertTrue(unclaimedWorker == null, "Expected unclaimed territory to deny claim worker growth.");
        helper.assertTrue(getClaimWorkers(level, claim).size() == baselineWorkers, "Expected hostile or unclaimed claim growth attempts to leave claim worker count unchanged.");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void claimGrownFarmerWaitsWithoutMarkedCropAreaEvenOnPreparedField(GameTestHelper helper) {
        WorkerSettlementSpawnRules.ClaimGrowthConfig config = claimGrowthConfig(20L, 4);

        ServerLevel level = helper.getLevel();
        String teamId = "phase31_claim_friendly_field";
        ServerPlayer leader = createLeader(helper, level, UUID.fromString("00000000-0000-0000-0000-000000003115"), "phase31-field-leader", teamId);
        BlockPos fieldCenter = helper.absolutePos(new BlockPos(8, 2, 8));
        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(level, fieldCenter, teamId, leader.getUUID(), leader.getScoreboardName());
        prepareField(level, fieldCenter);

        AbstractWorkerEntity worker = WorkersVillagerEvents.attemptClaimWorkerGrowth(level, claim, teamId, 20L, config);

        helper.assertTrue(worker instanceof FarmerEntity, "Expected the configured claim-growth profession pool to spawn a farmer.");
        FarmerEntity farmer = (FarmerEntity) worker;
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(farmer.getCurrentCropArea() == null,
                    "Expected prepared farmland alone not to auto-bind or create a crop area for a claim-grown farmer.");
            List<CropArea> cropAreas = level.getEntitiesOfClass(CropArea.class, new AABB(fieldCenter).inflate(12.0D));
            helper.assertTrue(cropAreas.isEmpty(),
                    "Expected claim growth to leave prepared farmland without a synthetic CropArea until a player-marked or validated zone exists.");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void claimGrownFarmerBindsExistingCropArea(GameTestHelper helper) {
        WorkerSettlementSpawnRules.ClaimGrowthConfig config = claimGrowthConfig(20L, 4, List.of(WorkerSettlementSpawnRules.WorkerProfession.FARMER));

        ServerLevel level = helper.getLevel();
        String teamId = "phase31_claim_autofield";
        ServerPlayer leader = createLeader(helper, level, UUID.fromString("00000000-0000-0000-0000-000000003116"), "phase31-autofield-leader", teamId);
        BlockPos claimPos = helper.absolutePos(new BlockPos(8, 2, 8));
        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(level, claimPos, teamId, leader.getUUID(), leader.getScoreboardName());
        prepareField(level, claimPos);
        CropArea existingArea = placeCropArea(level, leader, claimPos);

        AbstractWorkerEntity worker = WorkersVillagerEvents.attemptClaimWorkerGrowth(level, claim, teamId, 20L, config);

        helper.assertTrue(worker instanceof FarmerEntity, "Expected the configured claim-growth profession pool to spawn a farmer.");
        FarmerEntity farmer = (FarmerEntity) worker;
        helper.succeedWhen(() -> {
            CropArea currentArea = farmer.getCurrentCropArea();
            helper.assertTrue(currentArea != null && currentArea.isAlive(),
                    "Expected claim-grown farmers to bind an existing claim crop area instead of waiting idle.");
            helper.assertTrue(existingArea.getUUID().equals(currentArea.getUUID()),
                    "Expected claim-grown farmer to reuse the existing player-marked crop area instead of spawning a new one.");
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void claimGrownFishermanWaitsWithoutMarkedFishingArea(GameTestHelper helper) {
        WorkerSettlementSpawnRules.ClaimGrowthConfig config = claimGrowthConfig(20L, 4, List.of(WorkerSettlementSpawnRules.WorkerProfession.FISHERMAN));

        ServerLevel level = helper.getLevel();
        String teamId = "phase31_claim_autofish";
        ServerPlayer leader = createLeader(helper, level, UUID.fromString("00000000-0000-0000-0000-000000003117"), "phase31-autofish-leader", teamId);
        BlockPos claimPos = helper.absolutePos(new BlockPos(8, 2, 8));
        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(level, claimPos, teamId, leader.getUUID(), leader.getScoreboardName());
        prepareFishingWater(level, claimPos.east(3));

        AbstractWorkerEntity worker = WorkersVillagerEvents.attemptClaimWorkerGrowth(level, claim, teamId, 20L, config);

        helper.assertTrue(worker instanceof FishermanEntity, "Expected the configured claim-growth profession pool to spawn a fisherman.");
        FishermanEntity fisherman = (FishermanEntity) worker;
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(fisherman.getCurrentFishingArea() == null,
                    "Expected nearby water alone not to auto-create or bind a fishing area for a claim-grown fisherman.");
            List<FishingArea> fishingAreas = level.getEntitiesOfClass(FishingArea.class, new AABB(claimPos).inflate(24.0D));
            helper.assertTrue(fishingAreas.isEmpty(),
                    "Expected claim growth to leave nearby water without a synthetic FishingArea until a player-marked or validated zone exists.");
            helper.succeed();
        });
    }

    private static ServerPlayer createLeader(GameTestHelper helper, ServerLevel level, UUID playerId, String name, String teamId) {
        Player player = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, playerId, name);
        BannerModDedicatedServerGameTestSupport.ensureFaction(level, teamId, playerId, name);
        BannerModDedicatedServerGameTestSupport.joinTeam(level, teamId, player);
        BlockPos spawnPos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        return (ServerPlayer) player;
    }

    private static List<AbstractWorkerEntity> getClaimWorkers(ServerLevel level, RecruitsClaim claim) {
        UUID leaderId = claim.getPlayerInfo() == null ? null : claim.getPlayerInfo().getUUID();
        String politicalEntityId = claim.getOwnerPoliticalEntityId() == null ? null : claim.getOwnerPoliticalEntityId().toString();
        ChunkPos anchorChunk = claim.getCenter() != null ? claim.getCenter() : new ChunkPos(0, 0);
        AABB claimBounds = new AABB(
                anchorChunk.getMinBlockX(),
                level.getMinBuildHeight(),
                anchorChunk.getMinBlockZ(),
                anchorChunk.getMaxBlockX() + 1.0D,
                level.getMaxBuildHeight(),
                anchorChunk.getMaxBlockZ() + 1.0D
        );
        return level.getEntitiesOfClass(AbstractWorkerEntity.class, claimBounds, worker -> {
            if (!worker.isAlive() || !claim.containsChunk(worker.chunkPosition())) {
                return false;
            }
            boolean ownerMatch = leaderId != null && leaderId.equals(worker.getOwnerUUID());
            boolean teamMatch = politicalEntityId != null && worker.getTeam() != null && politicalEntityId.equals(
                    BannerModDedicatedServerGameTestSupport.politicalEntityIdString(level, worker.getTeam().getName()));
            return ownerMatch || teamMatch;
        });
    }

    private static CropArea placeCropArea(ServerLevel level, ServerPlayer leader, BlockPos center) {
        CropArea cropArea = new CropArea(ModEntityTypes.CROPAREA.get(), level);
        cropArea.setWidthSize(9);
        cropArea.setHeightSize(2);
        cropArea.setDepthSize(9);
        cropArea.setFacing(Direction.NORTH);
        cropArea.moveTo(center.getX() - 4, center.getY(), center.getZ() + 4, 0.0F, 0.0F);
        cropArea.createArea();
        cropArea.setDone(false);
        cropArea.setTeamStringID(leader.getTeam() == null ? "" : leader.getTeam().getName());
        cropArea.setPlayerUUID(leader.getUUID());
        cropArea.setPlayerName(leader.getScoreboardName());
        level.addFreshEntity(cropArea);
        WorkAreaIndex.instance().onEntityJoin(cropArea);
        return cropArea;
    }

    private static WorkerSettlementSpawnRules.ClaimGrowthConfig claimGrowthConfig(long baseCooldownTicks, int workerCap) {
        return claimGrowthConfig(baseCooldownTicks, workerCap, List.of(WorkerSettlementSpawnRules.WorkerProfession.FARMER));
    }

    private static WorkerSettlementSpawnRules.ClaimGrowthConfig claimGrowthConfig(long baseCooldownTicks,
                                                                                  int workerCap,
                                                                                  List<WorkerSettlementSpawnRules.WorkerProfession> professions) {
        return new WorkerSettlementSpawnRules.ClaimGrowthConfig(
                true,
                baseCooldownTicks,
                workerCap,
                professions
        );
    }

    private static void prepareField(ServerLevel level, BlockPos center) {
        level.setBlockAndUpdate(center, Blocks.WATER.defaultBlockState());
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, 1, dz), Blocks.WHEAT.defaultBlockState());
            }
        }
    }

    private static void prepareFishingWater(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(center.offset(dx, -1, dz), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(center.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
            }
        }
    }

}
