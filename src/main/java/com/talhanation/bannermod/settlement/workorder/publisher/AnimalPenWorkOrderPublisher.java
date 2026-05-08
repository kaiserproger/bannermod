package com.talhanation.bannermod.settlement.workorder.publisher;

import com.talhanation.bannermod.ai.civilian.AnimalFarmerLoopProgress;
import com.talhanation.bannermod.entity.civilian.workarea.AnimalPenArea;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublishContext;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublisherRegistry;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;

import java.util.ArrayList;
import java.util.List;

/** Emits animal-husbandry work orders in the same action order as AnimalFarmerLoopProgress. */
public final class AnimalPenWorkOrderPublisher implements SettlementWorkOrderPublisher {

    private static final int PRIORITY_BREED = 90;
    private static final int PRIORITY_SPECIAL_TASK = 80;
    private static final int PRIORITY_SLAUGHTER = 70;

    @Override
    public boolean matches(BannerModSettlementBuildingRecord building) {
        return SettlementWorkOrderPublisherRegistry.matchesBuildingType(building, "animal_pen_area");
    }

    @Override
    public void publish(SettlementWorkOrderPublishContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(ctx.building().buildingUuid());
        if (!(entity instanceof AnimalPenArea pen) || !pen.isAlive()) {
            return;
        }

        publishPlannedOrders(ctx, plan(pen));
    }

    static List<PlannedAnimalOrder> plan(AnimalPenArea pen) {
        pen.scanAnimalBreed();
        pen.scanAnimalSpecial();
        pen.scanAnimalSlaughter();

        AnimalFarmerLoopProgress.Decision decision = AnimalFarmerLoopProgress.selectNextAction(
                pen.getBreed(), pen.isBreedTime(), pen.animalsToBreed.size(),
                pen.getSpecial(), pen.animalsForSpecialTask.size(), pen.getAnimalType() == AnimalPenArea.AnimalTypes.CHICKEN,
                pen.getSlaughter(), pen.animalsToSlaughter.size(), pen.getMaxAnimals());

        List<PlannedAnimalOrder> planned = new ArrayList<>();
        switch (decision.action()) {
            case PREPARE_BREED -> {
                int amountToBreed = pen.animalsToBreed.size() - (pen.animalsToBreed.size() % 2);
                for (int i = 0; i < amountToBreed; i++) {
                    planned.add(new PlannedAnimalOrder(SettlementWorkOrderType.ANIMAL_BREED,
                            pen.animalsToBreed.get(i).blockPosition(), PRIORITY_BREED));
                }
            }
            case PREPARE_SPECIAL_TASK -> {
                if (pen.getAnimalType() == AnimalPenArea.AnimalTypes.CHICKEN) {
                    planned.add(new PlannedAnimalOrder(SettlementWorkOrderType.ANIMAL_SPECIAL_TASK,
                            BlockPos.containing(pen.getArea().getCenter()), PRIORITY_SPECIAL_TASK));
                } else {
                    for (Animal animal : pen.animalsForSpecialTask) {
                        planned.add(new PlannedAnimalOrder(SettlementWorkOrderType.ANIMAL_SPECIAL_TASK,
                                animal.blockPosition(), PRIORITY_SPECIAL_TASK));
                    }
                }
            }
            case PREPARE_SLAUGHTER -> {
                int amountToSlaughter = pen.animalsToSlaughter.size() - pen.getMaxAnimals();
                for (int i = 0; i < amountToSlaughter; i++) {
                    planned.add(new PlannedAnimalOrder(SettlementWorkOrderType.ANIMAL_SLAUGHTER,
                            pen.animalsToSlaughter.get(i).blockPosition(), PRIORITY_SLAUGHTER));
                }
            }
            default -> {
            }
        }
        return planned;
    }

    private void publishPlannedOrders(SettlementWorkOrderPublishContext ctx, List<PlannedAnimalOrder> plannedOrders) {
        for (PlannedAnimalOrder planned : plannedOrders) {
            ctx.runtime().publish(SettlementWorkOrder.pending(
                    ctx.claimUuid(),
                    ctx.building().buildingUuid(),
                    planned.type(),
                    planned.target(),
                    null,
                    planned.priority(),
                    ctx.gameTime()
            ));
        }
    }

    record PlannedAnimalOrder(SettlementWorkOrderType type, BlockPos target, int priority) {
    }
}
