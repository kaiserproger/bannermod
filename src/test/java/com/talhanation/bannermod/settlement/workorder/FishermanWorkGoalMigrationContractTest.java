package com.talhanation.bannermod.settlement.workorder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level migration contract for WORKGOAL-007.
 *
 * <p>For a fixed fishing target where the fisherman has a rod and inventory space, the legacy
 * FishermanWorkGoal output was: switch to a rod, swing/throw, spawn a bobber, splash, swing/retrieve,
 * generate fishing loot, discard the bobber, count the catch, and damage the rod. SettlementOrderWorkGoal
 * owns that same observable output for settlement FISH orders.</p>
 */
class FishermanWorkGoalMigrationContractTest {
    private static final Path ROOT = Path.of("");

    private static final String FISHERMAN_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/FishermanEntity.java";
    private static final String ABSTRACT_WORKER_ENTITY =
            "src/main/java/com/talhanation/bannermod/entity/civilian/AbstractWorkerEntity.java";
    private static final String LEGACY_FISHERMAN_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/FishermanWorkGoal.java";
    private static final String SETTLEMENT_GOAL =
            "src/main/java/com/talhanation/bannermod/ai/civilian/SettlementOrderWorkGoal.java";
    private static final String FISHING_PUBLISHER =
            "src/main/java/com/talhanation/bannermod/settlement/workorder/publisher/FishingAreaWorkOrderPublisher.java";

    @Test
    void fishermanRegistersSettlementOrderWorkGoalOnly() throws IOException {
        String fisherman = read(FISHERMAN_ENTITY);
        String worker = read(ABSTRACT_WORKER_ENTITY);

        assertFalse(Files.exists(ROOT.resolve(LEGACY_FISHERMAN_GOAL)),
                "FishermanWorkGoal must be deleted from src/main");
        assertTrue(worker.contains("new SettlementOrderWorkGoal(this)"),
                "AbstractWorkerEntity must execute settlement work orders for fishermen through super.registerGoals()");
        assertFalse(fisherman.contains("FishermanWorkGoal"),
                "FishermanEntity must not reference the legacy fisherman goal");
        assertFalse(fisherman.contains("new SettlementOrderWorkGoal(this)"),
                "FishermanEntity must not register a duplicate settlement-order goal");
    }

    @Test
    void fishingAreaOrdersFeedTheGenericFishingOutputPath() throws IOException {
        String publisher = read(FISHING_PUBLISHER);
        String goal = read(SETTLEMENT_GOAL);

        assertTrue(publisher.contains("SettlementWorkOrderType.FISH"),
                "publisher must label fixed fishing targets as FISH orders");
        assertTrue(publisher.contains("fishingArea.getOnPos().immutable()"),
                "publisher must emit the fixed fishing-area target position");

        int fishCase = goal.indexOf("case FISH");
        int switchRod = goal.indexOf("fisherman.switchMainHandItem", fishCase);
        int throwSwing = goal.indexOf("fisherman.swing(InteractionHand.MAIN_HAND)", switchRod);
        int throwSound = goal.indexOf("SoundEvents.FISHING_BOBBER_THROW", throwSwing);
        int throwHook = goal.indexOf("fisherman.throwFishingHook(target.getCenter())", throwSound);
        int splashSound = goal.indexOf("SoundEvents.FISHING_BOBBER_SPLASH", throwHook);
        int retrieveSwing = goal.indexOf("fisherman.swing(InteractionHand.MAIN_HAND)", splashSound);
        int retrieveSound = goal.indexOf("SoundEvents.FISHING_BOBBER_RETRIEVE", retrieveSwing);
        int spawnLoot = goal.indexOf("fisherman.spawnFishingLoot(fishingBobber)", retrieveSound);
        int discard = goal.indexOf("fishingBobber.discard()", spawnLoot);
        int countCatch = goal.indexOf("fisherman.farmedItems++", discard);
        int damageRod = goal.indexOf("fisherman.damageMainHandItem()", countCatch);
        int complete = goal.indexOf("completeActiveOrder(runtime, level)", damageRod);

        assertTrue(fishCase >= 0, "SettlementOrderWorkGoal must handle FISH orders");
        assertTrue(switchRod > fishCase,
                "fixed fishing target must select a fishing rod before casting");
        assertTrue(throwSwing > switchRod,
                "fixed fishing target must swing before the throw sound, matching FishermanWorkGoal output");
        assertTrue(throwSound > throwSwing,
                "fixed fishing target must play the throw sound after swinging");
        assertTrue(throwHook > throwSound,
                "fixed fishing target must spawn a fishing bobber after the throw sound");
        assertTrue(splashSound > throwHook,
                "fixed fishing target must reach the splash/catch output path");
        assertTrue(retrieveSwing > splashSound,
                "fixed fishing target must swing to retrieve after the splash");
        assertTrue(retrieveSound > retrieveSwing,
                "fixed fishing target must play the retrieve sound after swinging");
        assertTrue(spawnLoot > retrieveSound,
                "fixed fishing target must generate fishing loot after retrieval");
        assertTrue(discard > spawnLoot,
                "fixed fishing target must discard the bobber after loot generation");
        assertTrue(countCatch > discard,
                "fixed fishing target must increment the farmed item counter");
        assertTrue(damageRod > countCatch,
                "fixed fishing target must damage the rod after counting the catch");
        assertTrue(complete > damageRod,
                "fixed fishing target must complete the settlement work order after legacy-equivalent output");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
