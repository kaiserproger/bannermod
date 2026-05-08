package com.talhanation.bannermod.settlement.workorder;

import com.talhanation.bannermod.ai.civilian.FarmerLoopProgress;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmerSettlementOrderParityTest {

    private static final UUID CLAIM = UUID.fromString("00000000-0000-0000-0000-0000000002f1");
    private static final UUID BUILDING = UUID.fromString("00000000-0000-0000-0000-0000000002b1");
    private static final UUID RESIDENT = UUID.fromString("00000000-0000-0000-0000-0000000002a1");

    @Test
    void settlementOrdersMatchLegacyFarmerCropActionOutput() {
        List<FarmerLoopProgress.Action> legacyOutput = legacyCropOutput(true, true, true);
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.PLANT_CROP, new BlockPos(1, 64, 1), null, 50, 10L));
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.TILL_SOIL, new BlockPos(1, 64, 2), null, 60, 11L));
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.HARVEST_CROP, new BlockPos(1, 64, 3), null, 80, 12L));

        SettlementWorkOrder harvest = runtime.claim(CLAIM, RESIDENT, null, 100L, 200L).orElseThrow();
        runtime.complete(harvest.orderUuid(), 101L);
        SettlementWorkOrder till = runtime.claim(CLAIM, RESIDENT, null, 102L, 200L).orElseThrow();
        runtime.complete(till.orderUuid(), 103L);
        SettlementWorkOrder plant = runtime.claim(CLAIM, RESIDENT, null, 104L, 200L).orElseThrow();
        runtime.complete(plant.orderUuid(), 105L);

        assertEquals(legacyOutput, List.of(
                FarmerLoopProgress.Action.PREPARE_BREAK_BLOCKS,
                FarmerLoopProgress.Action.PREPARE_PLOWING,
                FarmerLoopProgress.Action.PREPARE_PLANT_SEEDS
        ));
        assertEquals(legacyOutput, List.of(
                toLegacyAction(harvest.type()),
                toLegacyAction(till.type()),
                toLegacyAction(plant.type())
        ));
        assertTrue(runtime.currentClaim(RESIDENT).isEmpty());
    }

    private static List<FarmerLoopProgress.Action> legacyCropOutput(boolean hasBlocksToBreak,
                                                                     boolean hasBlocksToPlow,
                                                                     boolean hasBlocksToPlant) {
        FarmerLoopProgress.Decision first = FarmerLoopProgress.selectNextAction(hasBlocksToBreak, hasBlocksToPlow, hasBlocksToPlant);
        FarmerLoopProgress.Decision second = FarmerLoopProgress.selectNextAction(false, hasBlocksToPlow, hasBlocksToPlant);
        FarmerLoopProgress.Decision third = FarmerLoopProgress.selectNextAction(false, false, hasBlocksToPlant);
        FarmerLoopProgress.Decision finished = FarmerLoopProgress.selectNextAction(false, false, false);
        assertTrue(finished.isFinished());
        return List.of(first.action(), second.action(), third.action());
    }

    private static FarmerLoopProgress.Action toLegacyAction(SettlementWorkOrderType type) {
        return switch (type) {
            case HARVEST_CROP -> FarmerLoopProgress.Action.PREPARE_BREAK_BLOCKS;
            case TILL_SOIL -> FarmerLoopProgress.Action.PREPARE_PLOWING;
            case PLANT_CROP -> FarmerLoopProgress.Action.PREPARE_PLANT_SEEDS;
            default -> throw new IllegalArgumentException("Unexpected farmer order type: " + type);
        };
    }
}
