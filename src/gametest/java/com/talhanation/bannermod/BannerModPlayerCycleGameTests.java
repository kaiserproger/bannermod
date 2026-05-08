package com.talhanation.bannermod;

import com.talhanation.bannermod.shared.logistics.BannerModSupplyStatus;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.gametest.support.RecruitsBattleGameTestSupport;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.persistence.civilian.BuildBlock;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModPlayerCycleGameTests {

    private static final String TEAM_ID = "phase06_cycle";
    private static final String DIVERGENT_TEAM_ID = "phase06_cycle_divergent";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void playerCycleStitchesOwnershipLaborUpkeepAndRecovery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        AbstractRecruitEntity recruit = RecruitsBattleGameTestSupport.spawnConfiguredRecruit(
                helper,
                com.talhanation.bannermod.registry.military.ModEntityTypes.RECRUIT.get(),
                RecruitsBattleGameTestSupport.WEST_FRONTLINE_POS,
                "Integrated Cycle Recruit",
                player.getUUID()
        );
        FarmerEntity worker = BannerModGameTestSupport.spawnOwnedFarmer(
                helper,
                player,
                RecruitsBattleGameTestSupport.WEST_FLANK_POS
        );
        CropArea cropArea = BannerModGameTestSupport.spawnOwnedCropArea(
                helper,
                player,
                RecruitsBattleGameTestSupport.WEST_RANGED_LEFT_POS
        );
        BuildArea buildArea = BannerModGameTestSupport.spawnOwnedBuildArea(
                helper,
                player,
                RecruitsBattleGameTestSupport.WEST_RANGED_RIGHT_POS
        );

        recruit.setTarget(worker);
        worker.setTarget(recruit);

        helper.assertFalse(RecruitEvents.canAttack(recruit, worker),
                "Expected the stitched player-cycle scenario to keep the shared-owner recruit from attacking the shared-owner worker");
        helper.assertFalse(RecruitEvents.canAttack(worker, recruit),
                "Expected the stitched player-cycle scenario to keep the shared-owner worker from attacking the shared-owner recruit");

        BannerModDedicatedServerGameTestSupport.joinTeam(level, TEAM_ID, player, worker);
        cropArea.setTeamStringID(TEAM_ID);
        buildArea.setTeamStringID(TEAM_ID);
        BannerModDedicatedServerGameTestSupport.seedClaim(level, helper.absolutePos(RecruitsBattleGameTestSupport.WEST_RANGED_LEFT_POS), TEAM_ID, player.getUUID(), player.getScoreboardName());
        worker.setCurrentWorkArea(cropArea);
        cropArea.setBeingWorkedOn(true);

        helper.assertTrue(cropArea.canWorkHere(worker),
                "Expected the stitched player-cycle scenario to let the shared-owner worker participate in the owned crop area");

        buildArea.setStructureNBT(BannerModGameTestSupport.createMinimalBuildTemplate());
        buildArea.setStartBuild(false);
        buildArea.stackToPlace.push(new BuildBlock(buildArea.blockPosition().above(), Blocks.OAK_PLANKS.defaultBlockState()));
        buildArea.stackToPlace.push(new BuildBlock(buildArea.blockPosition().above(2), Blocks.OAK_PLANKS.defaultBlockState()));
        buildArea.stackToPlace.push(new BuildBlock(buildArea.blockPosition().above(3), Blocks.OAK_PLANKS.defaultBlockState()));
        buildArea.stackToPlace.push(new BuildBlock(buildArea.blockPosition().above(4), Blocks.OAK_PLANKS.defaultBlockState()));

        recruit.forcedUpkeep = true;
        recruit.paymentTimer = 0;
        recruit.setHunger(1.0F);
        recruit.setUpkeepPos(buildArea.blockPosition());

        BannerModSupplyStatus.BuildProjectStatus buildStatus = BannerModSupplyStatus.buildProjectStatus(
                buildArea.hasStructureTemplate(),
                buildArea.hasPendingBuildWork(),
                buildArea.getRequiredMaterials()
        );
        SimpleContainer upkeepContainer = new SimpleContainer(9);
        BannerModSupplyStatus.RecruitSupplyStatus blockedRecruitStatus = recruit.getSupplyStatus(upkeepContainer);

        helper.assertTrue(buildArea.getPlayerUUID().equals(player.getUUID()),
                "Expected the stitched player-cycle settlement source and recruit upkeep source to share one owner");
        helper.assertTrue(buildStatus.state() == BannerModSupplyStatus.BuildState.NEEDS_MATERIALS,
                "Expected the stitched player-cycle build area to begin with material pressure before resupply");
        helper.assertTrue(blockedRecruitStatus.state() == BannerModSupplyStatus.RecruitSupplyState.NEEDS_FOOD_AND_PAYMENT,
                "Expected the stitched player-cycle recruit to begin blocked on food and payment before resupply");

        upkeepContainer.setItem(0, new ItemStack(Items.BREAD));
        ItemStack currency = BannerModDedicatedServerGameTestSupport.recruitCurrencyStack();
        currency.setCount(16);
        upkeepContainer.setItem(1, currency);
        BannerModSupplyStatus.RecruitSupplyStatus readyRecruitStatus = recruit.getSupplyStatus(upkeepContainer);

        helper.assertTrue(readyRecruitStatus.state() == BannerModSupplyStatus.RecruitSupplyState.READY,
                "Expected the stitched player-cycle recruit to reach READY after the same-owner upkeep container is resupplied");
        helper.assertFalse(readyRecruitStatus.blocked(),
                "Expected the stitched player-cycle recruit to stop reporting blocked upkeep after resupply");

        helper.assertTrue(worker.recoverControl(player),
                "Expected the stitched player-cycle owner to recover worker control safely at the end of the loop");
        helper.assertFalse(cropArea.isBeingWorkedOn(),
                "Expected stitched player-cycle recovery to release the claimed crop area");
        helper.assertTrue(worker.getCurrentWorkArea() == null,
                "Expected stitched player-cycle recovery to clear the worker's current crop-area binding");

        worker.setOwnerUUID(Optional.of(UUID.randomUUID()));
        BannerModDedicatedServerGameTestSupport.joinTeam(level, DIVERGENT_TEAM_ID, worker);

        helper.assertTrue(RecruitEvents.canAttack(recruit, worker),
                "Expected the stitched player-cycle scenario to end with an explicit divergent-ownership hostility boundary");
        helper.assertFalse(cropArea.canWorkHere(worker),
                "Expected the stitched player-cycle scenario to end with explicit work-area rejection after ownership diverges");
        helper.succeed();
    }
}
