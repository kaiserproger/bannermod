package com.talhanation.bannermod.settlement.workorder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level migration contract for WORKGOAL-005.
 *
 * <p>The legacy LumberjackWorkGoal fixed wood-cutting output for a selected log was:
 * call mineBlock(target), then swing the main hand. The generic SettlementOrderWorkGoal
 * FELL_TREE branch owns the same observable world mutation path for settlement tree orders.
 */
class LumberjackWorkGoalMigrationContractTest {
    private static final Path ROOT = Path.of("");

    private static final String LUMBERJACK_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/LumberjackEntity.java";
    private static final String ABSTRACT_WORKER_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/AbstractWorkerEntity.java";
    private static final String LEGACY_LUMBERJACK_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/LumberjackWorkGoal.java";
    private static final String SETTLEMENT_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/SettlementOrderWorkGoal.java";
    private static final String LUMBER_PUBLISHER =
            "src/main/java/com/talhanation/bannermod/settlement/workorder/publisher/LumberAreaWorkOrderPublisher.java";

    @Test
    void lumberjackRegistersSettlementOrderWorkGoalOnly() throws IOException {
        String lumberjack = read(LUMBERJACK_ENTITY);
        String worker = read(ABSTRACT_WORKER_ENTITY);

        assertFalse(Files.exists(ROOT.resolve(LEGACY_LUMBERJACK_GOAL)),
                "LumberjackWorkGoal must be deleted from src/main");
        assertTrue(worker.contains("new SettlementOrderWorkGoal(this)"),
                "AbstractWorkerEntity must execute settlement work orders for lumberjacks through super.registerGoals()");
        assertFalse(lumberjack.contains("new SettlementOrderWorkGoal(this)"),
                "LumberjackEntity must not register a duplicate settlement-order goal");
        assertFalse(lumberjack.contains("LumberjackWorkGoal"),
                "LumberjackEntity must not reference the legacy lumberjack goal");
    }

    @Test
    void lumberAreaOrdersFeedTheGenericFellTreeOutputPath() throws IOException {
        String publisher = read(LUMBER_PUBLISHER);
        String goal = read(SETTLEMENT_GOAL);

        assertTrue(publisher.contains("lumberArea.scanForTrees()"),
                "lumber orders must come from the same scanned tree targets as LumberjackWorkGoal");
        assertTrue(publisher.contains("for (Tree tree : lumberArea.stackOfTrees)"),
                "publisher must emit one order per fixed tree target");
        assertTrue(publisher.contains("SettlementWorkOrderType.FELL_TREE"),
                "publisher must label those targets as FELL_TREE orders");

        int fellCase = goal.indexOf("FELL_TREE");
        int mineCall = goal.indexOf("worker.mineBlock(target)", fellCase);
        int swingCall = goal.indexOf("worker.swing(InteractionHand.MAIN_HAND)", mineCall);

        assertTrue(fellCase >= 0, "SettlementOrderWorkGoal must handle FELL_TREE orders");
        assertTrue(mineCall > fellCase,
                "fixed solid FELL_TREE target must call mineBlock(target), matching LumberjackWorkGoal output");
        assertTrue(swingCall > mineCall,
                "fixed solid FELL_TREE target must swing after mining, matching LumberjackWorkGoal output");
    }

    @Test
    void fellTreeCompletionMatchesLegacyEmptyTargetOutput() throws IOException {
        String goal = read(SETTLEMENT_GOAL);
        int fellCase = goal.indexOf("FELL_TREE");
        int airCheck = goal.indexOf("state.isAir()", fellCase);
        int brokenCheck = goal.indexOf("AbstractWorkerEntity.isPosBroken(target, level, true)", fellCase);
        int complete = goal.indexOf("completeActiveOrder(runtime, level)", fellCase);

        assertTrue(airCheck > fellCase,
                "empty fixed fell-tree targets must be treated as already handled");
        assertTrue(brokenCheck > airCheck,
                "externally broken fixed fell-tree targets must be treated as already handled");
        assertTrue(complete > brokenCheck,
                "completed/empty FELL_TREE targets must close the order instead of emitting extra output");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
