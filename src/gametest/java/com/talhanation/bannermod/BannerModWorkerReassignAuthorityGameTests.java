package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.citizen.CitizenProfession;
import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.LumberjackEntity;
import com.talhanation.bannermod.entity.civilian.WorkerCitizenConversionService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModWorkerReassignAuthorityGameTests {
    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000001a01");
    private static final UUID OUTSIDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000001a02");
    private static final BlockPos WORKER_POS = new BlockPos(3, 2, 3);

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void nonOwnerReassignDeniedAndWorkerStateUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos workerAbsolutePos = helper.absolutePos(WORKER_POS);
        ServerPlayer owner = createPlayer(level, OWNER_UUID, "workerui-001a-owner", workerAbsolutePos);
        ServerPlayer outsider = createPlayer(level, OUTSIDER_UUID, "workerui-001a-outsider", workerAbsolutePos);
        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, WORKER_POS);

        UUID workerUuid = worker.getUUID();
        UUID ownerUuid = worker.getOwnerUUID();
        int followState = worker.getFollowState();
        BlockPos blockPos = worker.blockPosition();

        String denialKey = WorkerCitizenConversionService.reassignProfession(
                outsider,
                worker,
                CitizenProfession.LUMBERJACK
        );

        helper.assertTrue("gui.bannermod.worker_screen.convert.denied.not_controller".equals(denialKey),
                "Expected non-owner worker reassignment to return the not-controller denial key");
        helper.assertFalse(worker.isRemoved(),
                "Expected denied reassignment to keep the original worker entity alive");
        helper.assertTrue(level.getEntity(workerUuid) == worker,
                "Expected denied reassignment to keep the same worker entity registered");
        helper.assertTrue(worker instanceof FarmerEntity,
                "Expected denied reassignment to keep the original farmer profession/entity type");
        helper.assertTrue(ownerUuid != null && ownerUuid.equals(worker.getOwnerUUID()),
                "Expected denied reassignment to preserve worker ownership");
        helper.assertTrue(worker.isOwned(),
                "Expected denied reassignment to preserve owned state");
        helper.assertTrue(followState == worker.getFollowState(),
                "Expected denied reassignment to preserve follow state");
        helper.assertTrue(blockPos.equals(worker.blockPosition()),
                "Expected denied reassignment to preserve worker position");
        helper.assertTrue(workersNear(level, workerAbsolutePos).size() == 1,
                "Expected denied reassignment not to spawn a replacement worker");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void ownerReassignSmokeReplacesWorkerProfession(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos workerAbsolutePos = helper.absolutePos(WORKER_POS);
        ServerPlayer owner = createPlayer(level, OWNER_UUID, "workerui-001a-owner-success", workerAbsolutePos);
        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, WORKER_POS);
        UUID oldWorkerUuid = worker.getUUID();

        String denialKey = WorkerCitizenConversionService.reassignProfession(
                owner,
                worker,
                CitizenProfession.LUMBERJACK
        );

        helper.assertTrue(denialKey == null,
                "Expected owner worker reassignment to succeed");
        helper.assertTrue(worker.isRemoved(),
                "Expected successful reassignment to remove the original worker");
        helper.assertTrue(level.getEntity(oldWorkerUuid) == null,
                "Expected successful reassignment to unregister the original worker entity");
        List<AbstractWorkerEntity> workers = workersNear(level, workerAbsolutePos);
        helper.assertTrue(workers.size() == 1,
                "Expected successful reassignment to leave exactly one replacement worker");
        AbstractWorkerEntity replacement = workers.get(0);
        helper.assertTrue(replacement instanceof LumberjackEntity,
                "Expected successful reassignment to spawn a lumberjack replacement");
        helper.assertTrue(OWNER_UUID.equals(replacement.getOwnerUUID()),
                "Expected successful reassignment to preserve owner UUID");
        helper.assertTrue(replacement.isOwned(),
                "Expected successful reassignment to preserve owned state");
        helper.succeed();
    }

    private static ServerPlayer createPlayer(ServerLevel level, UUID playerId, String name, BlockPos pos) {
        return (ServerPlayer) BannerModDedicatedServerGameTestSupport.createPositionedFakeServerPlayer(level, playerId, name, pos);
    }

    private static List<AbstractWorkerEntity> workersNear(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(AbstractWorkerEntity.class, new AABB(pos).inflate(2.0D));
    }
}
