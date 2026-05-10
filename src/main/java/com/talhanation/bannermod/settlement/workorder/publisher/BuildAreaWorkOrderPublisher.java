package com.talhanation.bannermod.settlement.workorder.publisher;

import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.persistence.civilian.BuildBlock;
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
 * Emits construction work orders from a {@link BuildArea}.
 *
 * <p>Two flavours: {@link SettlementWorkOrderType#BREAK_BLOCK} for tiles that must be cleared
 * before placement, and {@link SettlementWorkOrderType#BUILD_BLOCK} for tiles whose target
 * state is known from the blueprint. Multi-block secondaries (upper door halves, bed heads)
 * are emitted as {@code BUILD_BLOCK} with the same mechanics as primaries; the builder AI
 * understands how to place them once the primary is down.</p>
 */
public final class BuildAreaWorkOrderPublisher implements SettlementWorkOrderPublisher {

    private static final int MAX_ORDERS_PER_AREA = 32;
    private static final int PRIORITY_BREAK = 70;
    private static final int PRIORITY_BUILD = 65;

    @Override
    public boolean matches(SettlementBuildingRecord building) {
        return SettlementWorkOrderPublisherRegistry.matchesBuildingType(building, "build_area");
    }

    @Override
    public void publish(SettlementWorkOrderPublishContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null) {
            return;
        }
        BuildArea buildArea = ctx.resolveBuildingEntity(BuildArea.class);
        if (buildArea == null) {
            return;
        }
        if (!buildArea.hasPendingBuildWork()) {
            return;
        }

        int emitted = 0;
        for (BlockPos breakPos : buildArea.stackToBreak) {
            if (emitted >= MAX_ORDERS_PER_AREA) {
                return;
            }
            if (publishOne(ctx, SettlementWorkOrderType.BREAK_BLOCK, breakPos, PRIORITY_BREAK)) {
                emitted++;
            }
        }
        for (BuildBlock placement : buildArea.stackToPlace) {
            if (emitted >= MAX_ORDERS_PER_AREA) {
                return;
            }
            if (publishOne(ctx, SettlementWorkOrderType.BUILD_BLOCK, placement.getPos(), PRIORITY_BUILD)) {
                emitted++;
            }
        }
        for (BuildBlock placement : buildArea.stackToPlaceMultiBlock) {
            if (emitted >= MAX_ORDERS_PER_AREA) {
                return;
            }
            if (publishOne(ctx, SettlementWorkOrderType.BUILD_BLOCK, placement.getPos(), PRIORITY_BUILD - 5)) {
                emitted++;
            }
        }
    }

    private boolean publishOne(SettlementWorkOrderPublishContext ctx,
                               SettlementWorkOrderType type,
                               BlockPos pos,
                               int priority) {
        if (pos == null) {
            return false;
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
        return ctx.runtime().publish(order).isPresent();
    }
}
