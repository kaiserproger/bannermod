package com.talhanation.bannermod.settlement.workorder;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.workorder.publisher.BuildAreaWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.publisher.CropAreaWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.publisher.FishingAreaWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.publisher.LumberAreaWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.publisher.MiningAreaWorkOrderPublisher;
import com.talhanation.bannermod.settlement.workorder.publisher.StockpileTransportWorkOrderPublisher;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ordered list of {@link SettlementWorkOrderPublisher}s. The first matching publisher for a
 * given building is invoked (though {@link #publishAll} runs every match, so a publisher may
 * contribute orders alongside a higher-priority peer if its {@link SettlementWorkOrderPublisher#matches}
 * returns true).
 */
public final class SettlementWorkOrderPublisherRegistry {
    private final List<SettlementWorkOrderPublisher> publishers = new ArrayList<>();

    /** Pre-populated registry containing publishers for every work-area type currently shipped. */
    public static SettlementWorkOrderPublisherRegistry defaults() {
        SettlementWorkOrderPublisherRegistry registry = new SettlementWorkOrderPublisherRegistry();
        registry.register(new CropAreaWorkOrderPublisher());
        registry.register(new FishingAreaWorkOrderPublisher());
        registry.register(new BuildAreaWorkOrderPublisher());
        registry.register(new LumberAreaWorkOrderPublisher());
        registry.register(new MiningAreaWorkOrderPublisher());
        registry.register(new StockpileTransportWorkOrderPublisher());
        return registry;
    }

    public void register(SettlementWorkOrderPublisher publisher) {
        Objects.requireNonNull(publisher, "publisher");
        publishers.add(publisher);
    }

    public List<SettlementWorkOrderPublisher> publishers() {
        return Collections.unmodifiableList(publishers);
    }

    public int size() {
        return publishers.size();
    }

    public static boolean matchesBuildingType(BannerModSettlementBuildingRecord building, String bareTypeId) {
        if (building == null || building.buildingTypeId() == null || bareTypeId == null || bareTypeId.isBlank()) {
            return false;
        }
        if (bareTypeId.equals(building.buildingTypeId())) {
            return true;
        }
        ResourceLocation typeId = ResourceLocation.tryParse(building.buildingTypeId());
        return typeId != null && bareTypeId.equals(typeId.getPath());
    }

    public void clear() {
        publishers.clear();
    }

    public void publishAll(SettlementWorkOrderPublishContext ctx) {
        BannerModSettlementBuildingRecord building = ctx.building();
        for (SettlementWorkOrderPublisher publisher : publishers) {
            if (publisher.matches(building)) {
                publisher.publish(ctx);
            }
        }
    }
}
