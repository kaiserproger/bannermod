package com.talhanation.bannermod.settlement.workorder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level migration contract for WORKGOAL-003.
 *
 * <p>The legacy MinerWorkGoal mine-block output for a fixed solid target was:
 * change to a valid tool, call mineBlock(target), then swing the main hand. The generic
 * SettlementOrderWorkGoal mine-block branch owns the same observable world mutation path for
 * settlement MINE_BLOCK orders.
 */
class MinerWorkGoalMigrationContractTest {
    private static final Path ROOT = Path.of("");

    private static final String MINER_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/MinerEntity.java";
    private static final String ABSTRACT_WORKER_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/AbstractWorkerEntity.java";
    private static final String LEGACY_MINER_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/MinerWorkGoal.java";
    private static final String SETTLEMENT_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/SettlementOrderWorkGoal.java";
    private static final String MINING_PUBLISHER =
            "src/main/java/com/talhanation/bannermod/settlement/workorder/publisher/MiningAreaWorkOrderPublisher.java";

    @Test
    void minerRegistersSettlementOrderWorkGoalOnly() throws IOException {
        String miner = read(MINER_ENTITY);
        String worker = read(ABSTRACT_WORKER_ENTITY);

        assertFalse(Files.exists(ROOT.resolve(LEGACY_MINER_GOAL)),
                "MinerWorkGoal must be deleted from src/main");
        assertTrue(worker.contains("new SettlementOrderWorkGoal(this)"),
                "AbstractWorkerEntity must execute settlement work orders for miners through super.registerGoals()");
        assertFalse(miner.contains("new SettlementOrderWorkGoal(this)"),
                "MinerEntity must not register a duplicate settlement-order goal");
        assertFalse(miner.contains("MinerWorkGoal"),
                "MinerEntity must not reference the legacy miner goal");
    }

    @Test
    void miningAreaOrdersFeedTheGenericMineBlockOutputPath() throws IOException {
        String publisher = read(MINING_PUBLISHER);
        String goal = read(SETTLEMENT_GOAL);

        assertTrue(publisher.contains("miningArea.scanBreakArea()"),
                "mining orders must come from the same scanned break targets as MinerWorkGoal");
        assertTrue(publisher.contains("for (BlockPos pos : miningArea.stackToBreak)"),
                "publisher must emit one order per fixed mine-block target");
        assertTrue(publisher.contains("SettlementWorkOrderType.MINE_BLOCK"),
                "publisher must label those targets as MINE_BLOCK orders");

        int mineCase = goal.indexOf("MINE_BLOCK");
        int mineCall = goal.indexOf("worker.mineBlock(target)", mineCase);
        int swingCall = goal.indexOf("worker.swing(InteractionHand.MAIN_HAND)", mineCall);

        assertTrue(mineCase >= 0, "SettlementOrderWorkGoal must handle MINE_BLOCK orders");
        assertTrue(mineCall > mineCase,
                "fixed solid MINE_BLOCK target must call mineBlock(target), matching MinerWorkGoal output");
        assertTrue(swingCall > mineCall,
                "fixed solid MINE_BLOCK target must swing after mining, matching MinerWorkGoal output");
    }

    @Test
    void mineBlockCompletionMatchesLegacyEmptyTargetOutput() throws IOException {
        String goal = read(SETTLEMENT_GOAL);
        int mineCase = goal.indexOf("MINE_BLOCK");
        int airCheck = goal.indexOf("state.isAir()", mineCase);
        int brokenCheck = goal.indexOf("AbstractWorkerEntity.isPosBroken(target, level, true)", mineCase);
        int complete = goal.indexOf("completeActiveOrder(runtime, level)", mineCase);

        assertTrue(airCheck > mineCase,
                "empty fixed mine-block targets must be treated as already handled");
        assertTrue(brokenCheck > airCheck,
                "externally broken fixed mine-block targets must be treated as already handled");
        assertTrue(complete > brokenCheck,
                "completed/empty MINE_BLOCK targets must close the order instead of emitting extra output");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
