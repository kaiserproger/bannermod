package com.talhanation.bannermod.entity.civilian;

import com.talhanation.bannermod.BannerModDedicatedServerGameTestSupport;
import com.talhanation.bannermod.BannerModStarterFortGameTestSupport;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.building.BuildingDefinitionRegistry;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.ZoneRole;
import com.talhanation.bannermod.settlement.building.ZoneSelection;
import com.talhanation.bannermod.settlement.bootstrap.BootstrapResult;
import com.talhanation.bannermod.settlement.bootstrap.SettlementBootstrapService;
import com.talhanation.bannermod.settlement.validation.BuildingValidationRequest;
import com.talhanation.bannermod.settlement.validation.BuildingValidationResult;
import com.talhanation.bannermod.settlement.validation.SettlementBuildingValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModStarterWorkerReadinessGameTests {

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 160)
    public static void starterBootstrapSeedsRealWorkerAssignmentsAndWaitingReasons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorkersServerConfig.setTestOverride(WorkersServerConfig.EnableClaimWorkerGrowth, false);
        Player leader = createLeader(helper, level,
                UUID.fromString("00000000-0000-0000-0000-000000021001"),
                "qa001-starter-leader",
                "qa001_starter_team",
                new BlockPos(1, 2, 1));
        BlockPos anchor = new BlockPos(30, 2, 30);

        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(
                level, anchor, "qa001_starter_team", leader.getUUID(), leader.getScoreboardName());
        helper.assertTrue(claim != null, "Expected bootstrap test claim to seed successfully.");

        BannerModStarterFortGameTestSupport.buildValidFort(level, anchor);
        BuildingValidationResult fortValidation = validateStarterFort(level, anchor);
        helper.assertTrue(fortValidation.valid(), "Expected prepared fort fixture to validate as STARTER_FORT.");

        BootstrapResult result = SettlementBootstrapService.bootstrapSettlement(level, leader, fortValidation);
        helper.assertTrue(result.success(), "Expected starter fort bootstrap to succeed.");

        helper.succeedWhen(() -> {
            FarmerEntity farmer = singleWorker(helper, level, FarmerEntity.class, anchor);
            MinerEntity miner = singleWorker(helper, level, MinerEntity.class, anchor);
            LumberjackEntity lumberjack = singleWorker(helper, level, LumberjackEntity.class, anchor);
            BuilderEntity builder = singleWorker(helper, level, BuilderEntity.class, anchor);

            assertAssignmentOrIdleReason(helper, farmer, "farmer_no_area");
            assertHasItem(helper, farmer, stack -> stack.getItem() instanceof HoeItem,
                    "Expected starter farmer to carry a hoe.");

            assertIdleReason(helper, miner, "miner_no_area");
            assertHasItem(helper, miner, stack -> stack.getItem() instanceof PickaxeItem,
                    "Expected starter miner to carry a pickaxe.");
            assertHasItem(helper, miner, stack -> stack.getItem() instanceof ShovelItem,
                    "Expected starter miner to carry a shovel.");
            assertIdleReason(helper, lumberjack, "lumberjack_no_area");
            assertHasItem(helper, lumberjack, stack -> stack.getItem() instanceof AxeItem,
                    "Expected starter lumberjack to carry an axe.");
            assertAssignmentOrIdleReason(helper, builder, "builder_no_area");
            assertHasItem(helper, builder, stack -> stack.getItem() instanceof AxeItem,
                    "Expected starter builder to carry an axe.");
            assertHasItem(helper, builder, stack -> stack.getItem() instanceof PickaxeItem,
                    "Expected starter builder to carry a pickaxe.");
            assertHasItem(helper, builder, stack -> stack.getItem() instanceof ShovelItem,
                    "Expected starter builder to carry a shovel.");
            WorkersServerConfig.clearAllTestOverrides();
        });
    }

    private static Player createLeader(GameTestHelper helper,
                                       ServerLevel level,
                                       UUID playerId,
                                       String name,
                                       String teamId,
                                       BlockPos relativePos) {
        Player player = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, playerId, name);
        BannerModDedicatedServerGameTestSupport.ensureFaction(level, teamId, playerId, name);
        BannerModDedicatedServerGameTestSupport.joinTeam(level, teamId, player);
        BlockPos absolute = helper.absolutePos(relativePos);
        player.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private static BuildingValidationResult validateStarterFort(ServerLevel level, BlockPos anchor) {
        return BannerModStarterFortGameTestSupport.validateStarterFort(level, anchor);
    }

    private static <T extends AbstractWorkerEntity> T singleWorker(GameTestHelper helper,
                                                                   ServerLevel level,
                                                                   Class<T> workerType,
                                                                   BlockPos anchor) {
        List<T> workers = level.getEntitiesOfClass(workerType, new AABB(anchor).inflate(24.0D));
        helper.assertTrue(workers.size() == 1,
                "Expected exactly one starter " + workerType.getSimpleName() + ", found " + workers.size() + ".");
        return workers.get(0);
    }

    private static void assertIdleReason(GameTestHelper helper, AbstractWorkerEntity worker, String expectedReason) {
        WorkerControlStatus status = worker.controlAccess().workStatus();
        helper.assertTrue(status.kind() == WorkerControlStatus.Kind.IDLE,
                "Expected idle work status for " + worker.getClass().getSimpleName() + " " + worker.getName().getString()
                        + ", got kind=" + status.kind() + ", reason=" + status.reasonToken()
                        + ", shouldWork=" + worker.shouldWork()
                        + ", needsSleep=" + worker.needsToSleep()
                        + ", currentWorkArea=" + worker.getCurrentWorkArea() + ".");
        helper.assertTrue(expectedReason.equals(status.reasonToken()),
                "Expected idle reason " + expectedReason + " for " + worker.getName().getString() + ", got " + status.reasonToken() + ".");
    }

    private static void assertAssignmentOrIdleReason(GameTestHelper helper, AbstractWorkerEntity worker, String expectedIdleReason) {
        if (worker.getCurrentWorkArea() != null) {
            return;
        }
        assertIdleReason(helper, worker, expectedIdleReason);
    }

    private static void assertHasItem(GameTestHelper helper,
                                      AbstractWorkerEntity worker,
                                      java.util.function.Predicate<net.minecraft.world.item.ItemStack> predicate,
                                      String message) {
        helper.assertTrue(worker.getMatchingItem(predicate) != null && !worker.getMatchingItem(predicate).isEmpty(), message);
    }

    private static void buildStarterField(ServerLevel level, BlockPos waterCenter) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos groundPos = waterCenter.offset(dx, 0, dz);
                BlockPos cropPos = groundPos.above();
                if (dx == 0 && dz == 0) {
                    level.setBlockAndUpdate(groundPos, Blocks.WATER.defaultBlockState());
                    level.setBlockAndUpdate(cropPos, Blocks.AIR.defaultBlockState());
                } else {
                    level.setBlockAndUpdate(groundPos, Blocks.FARMLAND.defaultBlockState());
                    level.setBlockAndUpdate(cropPos, Blocks.WHEAT.defaultBlockState());
                }
            }
        }
    }
}
