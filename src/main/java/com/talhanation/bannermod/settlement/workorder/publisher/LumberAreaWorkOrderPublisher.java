package com.talhanation.bannermod.settlement.workorder.publisher;

import com.talhanation.bannermod.entity.civilian.workarea.LumberArea;
import com.talhanation.bannermod.persistence.civilian.Tree;
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
 * Emits {@link SettlementWorkOrderType#FELL_TREE} orders for each scanned tree trunk and
 * {@link SettlementWorkOrderType#REPLANT_TREE} orders for saplable locations inside the area.
 *
 * <p>Only one order per tree is emitted (pointing at the lowest log), under the assumption
 * the existing lumberjack AI handles the rest of the tree once it arrives.</p>
 */
public final class LumberAreaWorkOrderPublisher implements SettlementWorkOrderPublisher {

    private static final int MAX_TREES_PER_PASS = 6;
    private static final int MAX_REPLANTS_PER_PASS = 6;
    private static final int PRIORITY_FELL = 60;
    private static final int PRIORITY_REPLANT = 40;

    @Override
    public boolean matches(SettlementBuildingRecord building) {
        return SettlementWorkOrderPublisherRegistry.matchesBuildingType(building, "lumber_area");
    }

    @Override
    public void publish(SettlementWorkOrderPublishContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null) {
            return;
        }
        LumberArea lumberArea = ctx.resolveBuildingEntity(LumberArea.class);
        if (lumberArea == null) {
            return;
        }

        lumberArea.stackOfTrees.clear();
        lumberArea.scanForTrees();
        lumberArea.scanPlantArea();

        int trees = 0;
        for (Tree tree : lumberArea.stackOfTrees) {
            if (trees >= MAX_TREES_PER_PASS) {
                break;
            }
            BlockPos origin = tree.getPosition();
            if (origin == null) {
                continue;
            }
            SettlementWorkOrder order = SettlementWorkOrder.pending(
                    ctx.claimUuid(),
                    ctx.building().buildingUuid(),
                    SettlementWorkOrderType.FELL_TREE,
                    origin.immutable(),
                    tree.toString(),
                    PRIORITY_FELL,
                    ctx.gameTime()
            );
            if (ctx.runtime().publish(order).isPresent()) {
                trees++;
            }
        }

        int replants = 0;
        for (BlockPos pos : lumberArea.getStackToPlant()) {
            if (replants >= MAX_REPLANTS_PER_PASS) {
                break;
            }
            SettlementWorkOrder order = SettlementWorkOrder.pending(
                    ctx.claimUuid(),
                    ctx.building().buildingUuid(),
                    SettlementWorkOrderType.REPLANT_TREE,
                    pos.immutable(),
                    null,
                    PRIORITY_REPLANT,
                    ctx.gameTime()
            );
            if (ctx.runtime().publish(order).isPresent()) {
                replants++;
            }
        }
    }
}
