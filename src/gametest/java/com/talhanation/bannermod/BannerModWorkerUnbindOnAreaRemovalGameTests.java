package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * WORKERUNBIND-001 acceptance gametest.
 *
 * <p>Verifies that when a worker's bound work-area entity is removed from the level,
 * the worker auto-unbinds within ~20 ticks: {@code boundWorkAreaUUID} returns to
 * {@code null} and {@code getCurrentWorkArea()} also returns {@code null}, so no
 * AI goal can still observe the dead area through the worker's binding.
 */
@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModWorkerUnbindOnAreaRemovalGameTests {

    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000005001");
    private static final String OWNER_TEAM_ID = "workerunbind_owner";

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty", timeoutTicks = 60)
    public static void workerAutoUnbindsWithinTwentyTicksAfterCropAreaRemoval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = BannerModDedicatedServerGameTestSupport.createFakeServerPlayer(level, OWNER_UUID, "workerunbind-owner");
        BannerModDedicatedServerGameTestSupport.ensureFaction(level, OWNER_TEAM_ID, OWNER_UUID, owner.getScoreboardName());
        BannerModDedicatedServerGameTestSupport.joinTeam(level, OWNER_TEAM_ID, owner);
        BlockPos spawnPos = helper.absolutePos(new BlockPos(1, 2, 1));
        owner.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);

        CropArea cropArea = BannerModGameTestSupport.spawnOwnedCropArea(helper, owner, new BlockPos(2, 2, 2));
        cropArea.setTeamStringID(OWNER_TEAM_ID);
        FarmerEntity farmer = BannerModGameTestSupport.spawnOwnedFarmer(helper, owner, new BlockPos(3, 2, 2));
        BannerModDedicatedServerGameTestSupport.joinTeam(level, OWNER_TEAM_ID, farmer);

        farmer.setCurrentWorkArea(cropArea);
        UUID boundBefore = farmer.getBoundWorkAreaUUID();
        helper.assertTrue(boundBefore != null, "Expected farmer to be bound to the crop area before removal");
        helper.assertTrue(farmer.getCurrentWorkArea() == cropArea,
                "Expected farmer.getCurrentWorkArea() to resolve to the live crop area before removal");

        // Remove the crop area entity. Equivalent to it being killed/discarded in-world.
        cropArea.discard();
        helper.assertTrue(cropArea.isRemoved(), "Crop area must report removed after discard()");

        // Advance ~20 game ticks and assert the worker auto-unbound.
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(farmer.getBoundWorkAreaUUID() == null,
                    "Expected farmer.boundWorkAreaUUID to be null within ~20 ticks after the crop area was removed");
            helper.assertTrue(farmer.getCurrentWorkArea() == null,
                    "Expected farmer.getCurrentWorkArea() to be null within ~20 ticks after the crop area was removed");
            helper.succeed();
        });
    }
}
