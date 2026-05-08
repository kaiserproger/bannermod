package com.talhanation.bannermod.settlement.workorder.publisher;

import com.talhanation.bannermod.entity.civilian.workarea.FishingArea;
import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublishContext;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublisherRegistry;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Emits one repeatable fishing order at the fishing area's water target. */
public final class FishingAreaWorkOrderPublisher implements SettlementWorkOrderPublisher {
    private static final int PRIORITY_FISH = 50;

    @Override
    public boolean matches(BannerModSettlementBuildingRecord building) {
        return SettlementWorkOrderPublisherRegistry.matchesBuildingType(building, "fishing_area");
    }

    @Override
    public void publish(SettlementWorkOrderPublishContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(ctx.building().buildingUuid());
        if (!(entity instanceof FishingArea fishingArea) || !fishingArea.isAlive()) {
            return;
        }

        SettlementWorkOrder order = SettlementWorkOrder.pending(
                ctx.claimUuid(),
                ctx.building().buildingUuid(),
                SettlementWorkOrderType.FISH,
                fishingArea.getOnPos().immutable(),
                null,
                PRIORITY_FISH,
                ctx.gameTime()
        );
        ctx.runtime().publish(order);
    }
}
