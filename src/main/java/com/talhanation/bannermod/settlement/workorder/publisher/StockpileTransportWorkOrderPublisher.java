package com.talhanation.bannermod.settlement.workorder.publisher;

import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublishContext;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderType;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsAuthoringState;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.UUID;

/**
 * Emits a single {@link SettlementWorkOrderType#HAUL_RESOURCE} order for every stockpile
 * building whose authored logistics route resolves to a live destination {@link StorageArea}.
 *
 * <p>The order describes a one-way item move from the source storage anchor to the
 * destination storage anchor; the worker-side execution in
 * {@code SettlementOrderWorkGoal} pulls items matching the route filter (or any item when the
 * filter is blank) up to the requested count, walks them to the destination, and deposits
 * them into any container in the destination's storage map.</p>
 *
 * <p>Duplicate-suppression in the runtime keys on {@code (building, type, targetPos)}, so
 * republishing for the same source/destination pair while the previous order is still
 * pending or claimed is a no-op.</p>
 */
public final class StockpileTransportWorkOrderPublisher implements SettlementWorkOrderPublisher {

    private static final int BASE_PRIORITY = 55;

    @Override
    public boolean matches(SettlementBuildingRecord building) {
        return building != null && building.stockpileBuilding() && building.stockpileRouteAuthored();
    }

    @Override
    public void publish(SettlementWorkOrderPublishContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null) {
            return;
        }
        StorageArea source = ctx.resolveBuildingEntity(StorageArea.class);
        if (source == null) {
            return;
        }
        BannerModLogisticsAuthoringState authoring = source.getLogisticsRouteAuthoringState();
        UUID destinationStorageId = authoring.destinationStorageAreaId();
        if (destinationStorageId == null || destinationStorageId.equals(source.getUUID())) {
            return;
        }
        Entity destinationEntity = level.getEntity(destinationStorageId);
        if (!(destinationEntity instanceof StorageArea destination) || !destination.isAlive()) {
            return;
        }

        BlockPos sourcePos = source.getOriginPos();
        BlockPos destinationPos = destination.getOriginPos();
        if (sourcePos == null || destinationPos == null) {
            return;
        }

        SettlementWorkOrder order = SettlementWorkOrder.pendingTransport(
                ctx.claimUuid(),
                ctx.building().buildingUuid(),
                SettlementWorkOrderType.HAUL_RESOURCE,
                sourcePos.immutable(),
                destinationPos.immutable(),
                resourceHintFromFilter(authoring.filterText()),
                Math.max(1, authoring.requestedCount()),
                priorityFor(authoring.priority()),
                ctx.gameTime()
        );
        ctx.runtime().publish(order);
    }

    static String resourceHintFromFilter(String filterText) {
        if (filterText == null) {
            return null;
        }
        String trimmed = filterText.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static int priorityFor(BannerModLogisticsPriority priority) {
        if (priority == null) {
            return BASE_PRIORITY;
        }
        return switch (priority) {
            case HIGH -> BASE_PRIORITY + 20;
            case LOW -> BASE_PRIORITY - 20;
            default -> BASE_PRIORITY;
        };
    }

    /**
     * Test helper that converts a token string ({@code "high"}, {@code "low"}, ...) into the
     * matching base priority. Mirrors {@link #priorityFor(BannerModLogisticsPriority)} for
     * tests that work with the persisted token rather than the enum.
     */
    public static int priorityForToken(String token) {
        if (token == null) {
            return BASE_PRIORITY;
        }
        try {
            return priorityFor(BannerModLogisticsPriority.valueOf(token.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return BASE_PRIORITY;
        }
    }
}
