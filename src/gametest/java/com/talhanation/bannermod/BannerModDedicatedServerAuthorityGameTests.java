package com.talhanation.bannermod;

import com.mojang.authlib.GameProfile;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.gametest.support.RecruitsCommandGameTestSupport;
import com.talhanation.bannermod.network.messages.military.MessageMovement;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModDedicatedServerAuthorityGameTests {

    private static final UUID OFFLINE_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final String OFFLINE_OWNER_NAME = "offline-owner";
    private static final UUID OUTSIDER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID ADMIN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final String OFFLINE_OWNER_TEAM_ID = "phase07_offline_owner";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void offlineOwnerAuthorityDeniesRecruitMovementCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player outsider = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, OUTSIDER_UUID, "offline-outsider");

        outsider.moveTo(helper.absolutePos(RecruitsBattleGameTestSupport.SquadAnchor.WEST.anchor()).getX() + 0.5D,
                helper.absolutePos(RecruitsBattleGameTestSupport.SquadAnchor.WEST.anchor()).getY(),
                helper.absolutePos(RecruitsBattleGameTestSupport.SquadAnchor.WEST.anchor()).getZ() + 0.5D,
                -90.0F,
                0.0F);
        outsider.setYRot(-90.0F);

        RecruitsBattleGameTestSupport.BattleSquad targetedSquad = RecruitsBattleGameTestSupport.spawnRecoveryPair(
                helper,
                RecruitsBattleGameTestSupport.SquadAnchor.WEST,
                OFFLINE_OWNER_UUID,
                "Offline Owner"
        );

        for (AbstractRecruitEntity recruit : targetedSquad.recruits()) {
            BannerModDedicatedServerGameTestSupport.assignDetachedOwnership(recruit, OFFLINE_OWNER_UUID);
            RecruitsCommandGameTestSupport.prepareForCommand(recruit, RecruitsCommandGameTestSupport.TARGET_GROUP_UUID);
        }

        helper.assertFalse(outsider.getUUID().equals(OFFLINE_OWNER_UUID),
                "Expected the dedicated-server outsider to stay distinct from the offline owner UUID");

        MessageMovement.dispatchToServer(
                outsider,
                outsider.getUUID(),
                RecruitsCommandGameTestSupport.TARGET_GROUP_UUID,
                1,
                0,
                false
        );

        for (AbstractRecruitEntity recruit : targetedSquad.recruits()) {
            RecruitsCommandGameTestSupport.assertUnchanged(recruit, 0, false);
        }

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void offlineOwnerAuthorityKeepsWorkerRecoveryOwnerOrAdminOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player temporaryOwner = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player outsider = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, OUTSIDER_UUID, "worker-outsider");
        Player admin = createAdminPlayer(level, ADMIN_UUID, "worker-admin");
        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(
                helper,
                temporaryOwner,
                RecruitsBattleGameTestSupport.WEST_FLANK_POS
        );
        CropArea cropArea = BannerModGameTestSupport.spawnOwnedCropArea(
                helper,
                temporaryOwner,
                RecruitsBattleGameTestSupport.WEST_RANGED_LEFT_POS
        );

        BannerModDedicatedServerGameTestSupport.assignDetachedOwnership(worker, OFFLINE_OWNER_UUID);
        BannerModDedicatedServerGameTestSupport.assignDetachedOwnership(cropArea, OFFLINE_OWNER_UUID, OFFLINE_OWNER_NAME);
        cropArea.setTeamStringID(OFFLINE_OWNER_TEAM_ID);
        BannerModDedicatedServerGameTestSupport.seedClaim(level, helper.absolutePos(RecruitsBattleGameTestSupport.WEST_RANGED_LEFT_POS), OFFLINE_OWNER_TEAM_ID, OFFLINE_OWNER_UUID, OFFLINE_OWNER_NAME);
        worker.setCurrentWorkArea(cropArea);
        cropArea.setBeingWorkedOn(true);

        helper.assertTrue(cropArea.canWorkHere(worker),
                "Expected detached UUID ownership alone to keep the worker authorized for the claimed crop area");
        helper.assertFalse(worker.recoverControl(outsider),
                "Expected unresolved-owner worker recovery to stay denied for a dedicated-server outsider");
        helper.assertTrue(cropArea.isBeingWorkedOn(),
                "Expected outsider recovery denial to preserve the claimed crop area when the owner player is unresolved");
        helper.assertTrue(worker.getCurrentWorkArea() == cropArea,
                "Expected outsider recovery denial to preserve the current crop-area binding for the offline-owned worker");
        helper.assertTrue(worker.recoverControl(admin),
                "Expected admin recovery to stay available even when the owner UUID has no live player entity");
        helper.assertFalse(cropArea.isBeingWorkedOn(),
                "Expected admin recovery to release the claimed crop area after authority is granted");
        helper.assertTrue(worker.getCurrentWorkArea() == null,
                "Expected admin recovery to clear the current crop-area binding after control is recovered");
        helper.succeed();
    }

    private static Player createAdminPlayer(ServerLevel level, UUID playerId, String name) {
        return new FakePlayer(level, new GameProfile(playerId, name)) {
            @Override
            public boolean hasPermissions(int permissionLevel) {
                return true;
            }
        };
    }
}
