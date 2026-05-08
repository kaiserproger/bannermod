package com.talhanation.bannermod.settlement.workorder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level migration contract for WORKGOAL-004.
 *
 * <p>For a fixed build-order scenario where the target is air and the builder carries the
 * blueprint item, the legacy BuilderWorkGoal output was: choose the matching item, place the
 * requested block, play the placement sound, swing, shrink the item stack, and remove the build
 * target. SettlementOrderWorkGoal must own that same observable mutation path.</p>
 */
class BuilderWorkGoalMigrationContractTest {
    private static final Path ROOT = Path.of("");

    private static final String BUILDER_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/BuilderEntity.java";
    private static final String ABSTRACT_WORKER_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/AbstractWorkerEntity.java";
    private static final String LEGACY_BUILDER_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/BuilderWorkGoal.java";
    private static final String SETTLEMENT_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/SettlementOrderWorkGoal.java";
    private static final String BUILD_PUBLISHER =
            "src/main/java/com/talhanation/bannermod/settlement/workorder/publisher/BuildAreaWorkOrderPublisher.java";

    @Test
    void builderRegistersSettlementOrderWorkGoalOnly() throws IOException {
        String builder = read(BUILDER_ENTITY);
        String worker = read(ABSTRACT_WORKER_ENTITY);

        assertFalse(Files.exists(ROOT.resolve(LEGACY_BUILDER_GOAL)),
                "BuilderWorkGoal must be deleted from src/main");
        assertTrue(worker.contains("new SettlementOrderWorkGoal(this)"),
                "AbstractWorkerEntity must execute settlement work orders for builders through super.registerGoals()");
        assertFalse(builder.contains("BuilderWorkGoal"),
                "BuilderEntity must not reference the legacy builder goal");
        assertFalse(builder.contains("new SettlementOrderWorkGoal(this)"),
                "BuilderEntity must not register a duplicate settlement-order goal");
    }

    @Test
    void buildAreaOrdersFeedTheGenericBuildBlockOutputPath() throws IOException {
        String publisher = read(BUILD_PUBLISHER);
        String goal = read(SETTLEMENT_GOAL);

        assertTrue(publisher.contains("for (BuildBlock placement : buildArea.stackToPlace)"),
                "publisher must emit one order per fixed build target");
        assertTrue(publisher.contains("SettlementWorkOrderType.BUILD_BLOCK"),
                "publisher must label fixed build targets as BUILD_BLOCK orders");

        int buildCase = goal.indexOf("BUILD_BLOCK");
        int matchingItem = goal.indexOf("worker.getMatchingItem", buildCase);
        int setBlock = goal.indexOf("level.setBlockAndUpdate(target, buildingState)", matchingItem);
        int playSound = goal.indexOf("level.playSound", setBlock);
        int swing = goal.indexOf("worker.swing(InteractionHand.MAIN_HAND)", playSound);
        int shrink = goal.indexOf("buildingItem.shrink(1)", swing);
        int remove = goal.indexOf("buildArea.removeBuildBlockToPlace(target)", shrink);
        int complete = goal.indexOf("completeActiveOrder(runtime, level)", remove);

        assertTrue(buildCase >= 0, "SettlementOrderWorkGoal must handle BUILD_BLOCK orders");
        assertTrue(matchingItem > buildCase,
                "fixed build target must select the matching blueprint item, matching BuilderWorkGoal output");
        assertTrue(setBlock > matchingItem,
                "fixed air build target must place the requested block, matching BuilderWorkGoal output");
        assertTrue(playSound > setBlock,
                "fixed build target must play the placement sound after placement");
        assertTrue(swing > playSound,
                "fixed build target must swing after the placement sound");
        assertTrue(shrink > swing,
                "fixed build target must consume one carried item after swinging");
        assertTrue(remove > shrink,
                "fixed build target must clear the build-area placement entry after consuming the item");
        assertTrue(complete > remove,
                "fixed build target must complete the settlement work order after legacy-equivalent output");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
