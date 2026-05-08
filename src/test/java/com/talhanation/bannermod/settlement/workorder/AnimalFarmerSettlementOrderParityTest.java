package com.talhanation.bannermod.settlement.workorder;

import com.talhanation.bannermod.ai.civilian.AnimalFarmerLoopProgress;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalFarmerSettlementOrderParityTest {

    private static final UUID CLAIM = UUID.fromString("00000000-0000-0000-0000-0000000008f1");
    private static final UUID BUILDING = UUID.fromString("00000000-0000-0000-0000-0000000008b1");
    private static final UUID RESIDENT = UUID.fromString("00000000-0000-0000-0000-0000000008a1");

    @Test
    void settlementOrdersMatchLegacyAnimalFarmerActionOutput() {
        List<AnimalFarmerLoopProgress.Action> legacyOutput = legacyAnimalOutput();
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.ANIMAL_SLAUGHTER, new BlockPos(1, 64, 3), null, 70, 12L));
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.ANIMAL_SPECIAL_TASK, new BlockPos(1, 64, 2), null, 80, 11L));
        runtime.publish(SettlementWorkOrder.pending(CLAIM, BUILDING,
                SettlementWorkOrderType.ANIMAL_BREED, new BlockPos(1, 64, 1), null, 90, 10L));

        SettlementWorkOrder breed = runtime.claim(CLAIM, RESIDENT, null, 100L, 200L).orElseThrow();
        runtime.complete(breed.orderUuid(), 101L);
        SettlementWorkOrder specialTask = runtime.claim(CLAIM, RESIDENT, null, 102L, 200L).orElseThrow();
        runtime.complete(specialTask.orderUuid(), 103L);
        SettlementWorkOrder slaughter = runtime.claim(CLAIM, RESIDENT, null, 104L, 200L).orElseThrow();
        runtime.complete(slaughter.orderUuid(), 105L);

        assertEquals(legacyOutput, List.of(
                toLegacyAction(breed.type()),
                toLegacyAction(specialTask.type()),
                toLegacyAction(slaughter.type())
        ));
        assertTrue(runtime.currentClaim(RESIDENT).isEmpty());
    }

    @Test
    void finishedAnimalLoopEmitsNoSettlementOrder() {
        AnimalFarmerLoopProgress.Decision finished = AnimalFarmerLoopProgress.selectNextAction(
                false, false, 0,
                false, 0, false,
                false, 0, 12);
        SettlementWorkOrderRuntime runtime = new SettlementWorkOrderRuntime();

        assertTrue(finished.isFinished());
        assertTrue(runtime.claim(CLAIM, RESIDENT, null, 100L, 200L).isEmpty());
    }

    @Test
    void defaultPublisherRegistryCoversAnimalPenBuildings() {
        BannerModSettlementBuildingRecord animalPen = new BannerModSettlementBuildingRecord(
                BUILDING, "bannermod:animal_pen_area", BlockPos.ZERO, null, null, 0, 1, 0, List.of());

        SettlementWorkOrderPublisher publisher = SettlementWorkOrderPublisherRegistry.defaults().publishers().stream()
                .filter(candidate -> candidate.matches(animalPen))
                .findFirst()
                .orElseThrow();

        assertInstanceOf(com.talhanation.bannermod.settlement.workorder.publisher.AnimalPenWorkOrderPublisher.class, publisher);
    }

    @Test
    void settlementOrderWorkGoalExecutesAnimalHusbandryTypes() throws IOException {
        String goal = Files.readString(Path.of("src/main/java/com/talhanation/bannermod/ai/civilian/SettlementOrderWorkGoal.java"));
        String animalFarmer = Files.readString(Path.of("src/main/java/com/talhanation/bannermod/ai/civilian/AnimalFarmerWorkGoal.java"));

        assertTrue(goal.contains("case ANIMAL_BREED -> executeAnimalBreed"));
        assertTrue(goal.contains("case ANIMAL_SPECIAL_TASK -> executeAnimalSpecialTask"));
        assertTrue(goal.contains("case ANIMAL_SLAUGHTER -> executeAnimalSlaughter"));
        assertTrue(goal.contains("ANIMAL_BREED,"));
        assertTrue(goal.contains("ANIMAL_SPECIAL_TASK,"));
        assertTrue(goal.contains("ANIMAL_SLAUGHTER,"));
        assertFalse(animalFarmer.contains("SettlementWorkOrderType.ANIMAL_BREED"));
        assertFalse(animalFarmer.contains("SettlementWorkOrderType.ANIMAL_SPECIAL_TASK"));
        assertFalse(animalFarmer.contains("SettlementWorkOrderType.ANIMAL_SLAUGHTER"));
    }

    private static List<AnimalFarmerLoopProgress.Action> legacyAnimalOutput() {
        AnimalFarmerLoopProgress.Decision first = AnimalFarmerLoopProgress.selectNextAction(true, true, 4,
                true, 2, false,
                true, 14, 12);
        AnimalFarmerLoopProgress.Decision second = AnimalFarmerLoopProgress.selectNextAction(false, false, 0,
                true, 2, false,
                true, 14, 12);
        AnimalFarmerLoopProgress.Decision third = AnimalFarmerLoopProgress.selectNextAction(false, false, 0,
                false, 0, false,
                true, 14, 12);
        AnimalFarmerLoopProgress.Decision finished = AnimalFarmerLoopProgress.selectNextAction(false, false, 0,
                false, 0, false,
                false, 0, 12);
        assertTrue(finished.isFinished());
        return List.of(first.action(), second.action(), third.action());
    }

    private static AnimalFarmerLoopProgress.Action toLegacyAction(SettlementWorkOrderType type) {
        return switch (type) {
            case ANIMAL_BREED -> AnimalFarmerLoopProgress.Action.PREPARE_BREED;
            case ANIMAL_SPECIAL_TASK -> AnimalFarmerLoopProgress.Action.PREPARE_SPECIAL_TASK;
            case ANIMAL_SLAUGHTER -> AnimalFarmerLoopProgress.Action.PREPARE_SLAUGHTER;
            default -> throw new IllegalArgumentException("Unexpected animal-farmer order type: " + type);
        };
    }
}
