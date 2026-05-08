package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModOwnershipCycleGameTests {

    private static final String TEAM_ID = "phase06_ownership";
    private static final String DIVERGENT_TEAM_ID = "phase06_ownership_divergent";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void sharedOwnershipKeepsPlayerCycleAuthorityAligned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        AbstractRecruitEntity recruit = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                com.talhanation.bannermod.registry.military.ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_FRONTLINE_POS,
                "Integrated Recruit",
                player.getUUID()
        );
        FarmerEntity worker = BannerModGameTestSupport.spawnEntity(
                helper,
                com.talhanation.bannermod.registry.civilian.ModEntityTypes.FARMER.get(),
                RecruitsBattleGameTestSupport.WEST_FLANK_POS
        );
        CropArea cropArea = BannerModGameTestSupport.spawnOwnedCropArea(
                helper,
                player,
                RecruitsBattleGameTestSupport.WEST_RANGED_LEFT_POS
        );

        worker.setCustomNameVisible(true);
        worker.setPersistenceRequired();
        recruit.setTarget(worker);
        worker.setTarget(recruit);

        helper.assertTrue(RecruitEvents.canAttack(recruit, worker),
                "Expected the player-cycle slice to begin with hostile recruit-to-worker targeting before ownership sync");
        helper.assertTrue(RecruitEvents.canAttack(worker, recruit),
                "Expected the player-cycle slice to begin with hostile worker-to-recruit targeting before ownership sync");

        worker.setOwnerUUID(Optional.of(player.getUUID()));
        worker.setIsOwned(true);
        worker.setFollowState(2);
        worker.setHoldPos(net.minecraft.world.phys.Vec3.atCenterOf(worker.blockPosition()));
        BannerModDedicatedServerGameTestSupport.joinTeam(level, TEAM_ID, player, worker);
        cropArea.setTeamStringID(TEAM_ID);
        BannerModDedicatedServerGameTestSupport.seedClaim(level, helper.absolutePos(RecruitsBattleGameTestSupport.WEST_RANGED_LEFT_POS), TEAM_ID, player.getUUID(), player.getScoreboardName());
        worker.setCurrentWorkArea(cropArea);
        cropArea.setBeingWorkedOn(true);

        helper.assertFalse(RecruitEvents.canAttack(recruit, worker),
                "Expected shared player ownership to stop recruit hostility toward the owned worker in the BannerMod gameplay cycle");
        helper.assertFalse(RecruitEvents.canAttack(worker, recruit),
                "Expected shared player ownership to stop worker hostility toward the owned recruit in the BannerMod gameplay cycle");
        helper.assertTrue(cropArea.canWorkHere(worker),
                "Expected the shared-owner crop area to accept the worker during the BannerMod ownership cycle slice");
        helper.assertTrue(worker.recoverControl(player),
                "Expected the owning player to recover worker control during the BannerMod ownership cycle slice");
        helper.assertFalse(cropArea.isBeingWorkedOn(),
                "Expected worker recovery to release the claimed crop area during the ownership cycle slice");
        helper.assertTrue(worker.getCurrentWorkArea() == null,
                "Expected worker recovery to clear the crop-area binding during the ownership cycle slice");

        worker.setOwnerUUID(Optional.of(UUID.randomUUID()));
        BannerModDedicatedServerGameTestSupport.joinTeam(level, DIVERGENT_TEAM_ID, worker);

        helper.assertTrue(RecruitEvents.canAttack(recruit, worker),
                "Expected divergent ownership to restore recruit hostility after the BannerMod ownership cycle splits actor control");
        helper.assertTrue(RecruitEvents.canAttack(worker, recruit),
                "Expected divergent ownership to restore worker hostility after the BannerMod ownership cycle splits actor control");
        helper.assertFalse(cropArea.canWorkHere(worker),
                "Expected divergent ownership to make the crop area reject the worker after the BannerMod ownership cycle loses shared authority");
        helper.succeed();
    }
}
