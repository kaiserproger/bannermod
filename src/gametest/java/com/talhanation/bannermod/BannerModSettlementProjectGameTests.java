package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingCategory;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.growth.ProjectBlocker;
import com.talhanation.bannermod.settlement.growth.ProjectKind;
import com.talhanation.bannermod.settlement.project.AssignmentPhase;
import com.talhanation.bannermod.settlement.project.BannerModBuildAreaProjectBridge;
import com.talhanation.bannermod.settlement.project.BannerModSettlementProjectRuntime;
import com.talhanation.bannermod.settlement.project.ProjectAssignment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(BannerModMain.MOD_ID)
public class BannerModSettlementProjectGameTests {

    public static void settlementProjectCreatesExecutableBuildAreaInWorld(GameTestHelper helper) {
        assertSettlementProjectCreatesExecutableBuildAreaInWorld(helper);
    }

    static void assertSettlementProjectCreatesExecutableBuildAreaInWorld(GameTestHelper helper) {
        // This test exercises the legacy prefab-driven settlement-project pipeline,
        // which the player has disabled by default in favour of manual build + surveyor.
        // Force the gate open for the duration of the test so we can keep validating the
        // pipeline contract; production code still observes the config flag.
        com.talhanation.bannermod.settlement.prefab.BuildingPlacementService
                .setPrefabEnabledOverrideForTesting(Boolean.TRUE);
        try {
            runAssertSettlementProjectCreatesExecutableBuildAreaInWorld(helper);
        } finally {
            com.talhanation.bannermod.settlement.prefab.BuildingPlacementService
                    .setPrefabEnabledOverrideForTesting(null);
        }
    }

    private static void runAssertSettlementProjectCreatesExecutableBuildAreaInWorld(GameTestHelper helper) {
        WorkAreaIndex.instance().clearAllForTest();
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        RecruitsClaim claim = BannerModDedicatedServerGameTestSupport.seedClaim(
                level,
                new BlockPos(2, 2, 2),
                "project-live-claim",
                player.getUUID(),
                player.getName().getString()
        );

        PendingProject project = new PendingProject(
                UUID.randomUUID(),
                ProjectKind.NEW_BUILDING,
                null,
                null,
                BannerModSettlementBuildingCategory.GENERAL,
                BannerModSettlementBuildingProfileSeed.GENERAL,
                100,
                level.getGameTime(),
                20,
                ProjectBlocker.NONE
        );

        BannerModSettlementProjectRuntime runtime = BannerModSettlementProjectRuntime.forServer(level);
        ProjectAssignment assignment = BannerModSettlementProjectRuntime.tickClaim(
                level,
                claim.getUUID(),
                List.of(project)
        ).orElseThrow();

        helper.assertTrue(assignment.phase() == AssignmentPhase.SEARCHING_BUILDER,
                "Expected live project execution to bind a prefab-backed BuildArea immediately");
        Entity entity = level.getEntity(assignment.buildAreaUuid());
        helper.assertTrue(entity instanceof BuildArea,
                "Expected project execution to create a real BuildArea entity in-world");
        BuildArea buildArea = (BuildArea) entity;
        helper.assertTrue(buildArea.hasStructureTemplate(),
                "Expected spawned project BuildArea to carry a real structure template");
        helper.assertTrue(buildArea.hasPendingBuildWork(),
                "Expected spawned project BuildArea to expose actionable world build work");
        helper.assertTrue(runtime.assignmentForBuildArea(buildArea.getUUID()).isPresent(),
                "Expected GAME-009 assignment tracking to reuse the spawned live BuildArea");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "harness_empty")
    public static void settlementProjectBindsToExecutableBuildAreaTarget(GameTestHelper helper) {
        assertSettlementProjectBindsToExecutableBuildAreaTarget(helper);
    }

    static void assertSettlementProjectBindsToExecutableBuildAreaTarget(GameTestHelper helper) {
        WorkAreaIndex.instance().clearAllForTest();
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BuildArea buildArea = BannerModGameTestSupport.spawnOwnedBuildArea(helper, player, new BlockPos(2, 2, 2));
        buildArea.setStructureNBT(BannerModGameTestSupport.createMinimalBuildTemplate());
        buildArea.setDone(false);

        RecruitsClaim claim = new RecruitsClaim("project-claim", null);
        ChunkPos buildAreaChunk = buildArea.chunkPosition();
        claim.setCenter(buildAreaChunk);
        claim.addChunk(buildAreaChunk);

        PendingProject project = new PendingProject(
                UUID.randomUUID(),
                ProjectKind.NEW_BUILDING,
                null,
                null,
                BannerModSettlementBuildingCategory.GENERAL,
                BannerModSettlementBuildingProfileSeed.GENERAL,
                100,
                level.getGameTime(),
                20,
                ProjectBlocker.NONE
        );

        Optional<ProjectAssignment> assignment = BannerModSettlementProjectRuntime.detachedForTests().tickClaim(
                level,
                claim.getUUID(),
                List.of(project),
                new BannerModBuildAreaProjectBridge.ClaimBuildAreaResolver(level, claim),
                level.getGameTime()
        );

        helper.assertTrue(assignment.isPresent(),
                "Expected settlement project creation to bind a live BuildArea instead of leaving a noop placeholder");
        ProjectAssignment resolved = assignment.get();
        helper.assertTrue(buildArea.getUUID().equals(resolved.buildAreaUuid()),
                "Expected project assignment to target the live BuildArea UUID");
        helper.assertTrue(resolved.phase() == AssignmentPhase.SEARCHING_BUILDER,
                "Expected template-backed BuildArea projects to be executable and search for a builder");
        helper.succeed();
    }

    static void assertSettlementProjectProgressesFromBuildExecutionEvents(GameTestHelper helper) {
        WorkAreaIndex.instance().clearAllForTest();
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BuildArea buildArea = BannerModGameTestSupport.spawnOwnedBuildArea(helper, player, new BlockPos(2, 2, 2));
        buildArea.setStructureNBT(BannerModGameTestSupport.createMinimalBuildTemplate());
        buildArea.setDone(false);

        RecruitsClaim claim = new RecruitsClaim("project-progress-claim", null);
        ChunkPos buildAreaChunk = buildArea.chunkPosition();
        claim.setCenter(buildAreaChunk);
        claim.addChunk(buildAreaChunk);

        PendingProject project = new PendingProject(
                UUID.randomUUID(),
                ProjectKind.NEW_BUILDING,
                null,
                null,
                BannerModSettlementBuildingCategory.GENERAL,
                BannerModSettlementBuildingProfileSeed.GENERAL,
                100,
                level.getGameTime(),
                20,
                ProjectBlocker.NONE
        );

        BannerModSettlementProjectRuntime runtime = BannerModSettlementProjectRuntime.forServer(level);
        ProjectAssignment assignment = runtime.tickClaim(
                level,
                claim.getUUID(),
                List.of(project),
                new BannerModBuildAreaProjectBridge.ClaimBuildAreaResolver(level, claim),
                level.getGameTime()
        ).orElseThrow();

        helper.assertTrue(assignment.phase() == AssignmentPhase.SEARCHING_BUILDER,
                "Expected assignment to wait for build execution before reporting progress");

        buildArea.setStartBuild(false);
        ProjectAssignment inProgress = runtime.assignmentForBuildArea(buildArea.getUUID()).orElseThrow();
        helper.assertTrue(inProgress.phase() == AssignmentPhase.IN_PROGRESS,
                "Expected real BuildArea start event to advance project progress");

        buildArea.setDone(true);
        ProjectAssignment completed = runtime.assignmentForBuildArea(buildArea.getUUID()).orElseThrow();
        helper.assertTrue(completed.phase() == AssignmentPhase.COMPLETED,
                "Expected real BuildArea completion event to complete the project");
        helper.succeed();
    }
}
