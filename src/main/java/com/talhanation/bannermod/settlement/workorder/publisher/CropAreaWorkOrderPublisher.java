package com.talhanation.bannermod.settlement.workorder.publisher;

import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublishContext;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublisherRegistry;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Emits farming work orders (till, plant, harvest) for buildings backed by a {@link CropArea}
 * entity. Delegates block-level scanning to the existing {@code scan*} helpers on
 * {@link CropArea} rather than re-implementing them.
 */
public final class CropAreaWorkOrderPublisher implements SettlementWorkOrderPublisher {

    /** Static cap on how many orders a single crop area may contribute per publish pass. */
    private static final int MAX_ORDERS_PER_AREA = 24;

    private static final int PRIORITY_HARVEST = 80;
    private static final int PRIORITY_TILL = 60;
    private static final int PRIORITY_PLANT = 50;

    @Override
    public boolean matches(SettlementBuildingRecord building) {
        return SettlementWorkOrderPublisherRegistry.matchesBuildingType(building, "crop_area");
    }

    @Override
    public void publish(SettlementWorkOrderPublishContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(ctx.building().buildingUuid());
        if (!(entity instanceof CropArea cropArea) || !cropArea.isAlive()) {
            return;
        }

        cropArea.scanBreakArea();
        cropArea.scanPlowArea();
        cropArea.scanPlantArea();

        int emitted = 0;
        emitted += publishPositions(ctx, cropArea.stackToBreak, SettlementWorkOrderType.HARVEST_CROP, PRIORITY_HARVEST, MAX_ORDERS_PER_AREA - emitted);
        emitted += publishPositions(ctx, cropArea.stackToPlow, SettlementWorkOrderType.TILL_SOIL, PRIORITY_TILL, MAX_ORDERS_PER_AREA - emitted);
        publishPositions(ctx, cropArea.stackToPlant, SettlementWorkOrderType.PLANT_CROP, PRIORITY_PLANT, MAX_ORDERS_PER_AREA - emitted);
    }

    private int publishPositions(SettlementWorkOrderPublishContext ctx,
                                 java.util.Collection<BlockPos> positions,
                                 SettlementWorkOrderType type,
                                 int priority,
                                 int budget) {
        if (positions == null || positions.isEmpty() || budget <= 0) {
            return 0;
        }
        int count = 0;
        for (BlockPos pos : positions) {
            if (count >= budget) {
                break;
            }
            SettlementWorkOrder order = SettlementWorkOrder.pending(
                    ctx.claimUuid(),
                    ctx.building().buildingUuid(),
                    type,
                    pos.immutable(),
                    null,
                    priority,
                    ctx.gameTime()
            );
            if (ctx.runtime().publish(order).isPresent()) {
                count++;
            }
        }
        return count;
    }
}
