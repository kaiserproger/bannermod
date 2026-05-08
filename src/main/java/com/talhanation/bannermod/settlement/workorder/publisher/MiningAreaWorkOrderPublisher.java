package com.talhanation.bannermod.settlement.workorder.publisher;

import com.talhanation.bannermod.entity.civilian.workarea.MiningArea;
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
 * Emits {@link SettlementWorkOrderType#MINE_BLOCK} orders for each scanned excavation target
 * in a {@link MiningArea}. The area owns its own mining mode / pattern state, which this
 * publisher re-scans idempotently each pass.
 */
public final class MiningAreaWorkOrderPublisher implements SettlementWorkOrderPublisher {

    private static final int MAX_ORDERS_PER_PASS = 32;
    private static final int PRIORITY_MINE = 55;

    @Override
    public boolean matches(SettlementBuildingRecord building) {
        return SettlementWorkOrderPublisherRegistry.matchesBuildingType(building, "mining_area");
    }

    @Override
    public void publish(SettlementWorkOrderPublishContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(ctx.building().buildingUuid());
        if (!(entity instanceof MiningArea miningArea) || !miningArea.isAlive()) {
            return;
        }

        miningArea.scanBreakArea();

        int emitted = 0;
        for (BlockPos pos : miningArea.stackToBreak) {
            if (emitted >= MAX_ORDERS_PER_PASS) {
                break;
            }
            SettlementWorkOrder order = SettlementWorkOrder.pending(
                    ctx.claimUuid(),
                    ctx.building().buildingUuid(),
                    SettlementWorkOrderType.MINE_BLOCK,
                    pos.immutable(),
                    null,
                    PRIORITY_MINE,
                    ctx.gameTime()
            );
            if (ctx.runtime().publish(order).isPresent()) {
                emitted++;
            }
        }
    }
}
