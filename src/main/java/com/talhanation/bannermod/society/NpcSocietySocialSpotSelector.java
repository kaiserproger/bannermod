package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public final class NpcSocietySocialSpotSelector {
    private NpcSocietySocialSpotSelector() {
    }

    public static Selection select(@Nullable BannerModSettlementSnapshot snapshot,
                                   @Nullable UUID homeBuildingUuid,
                                   boolean preferHome) {
        if (preferHome) {
            Vec3 homePos = buildingCenter(snapshot, homeBuildingUuid);
            if (homePos != null) {
                return new Selection(homePos, "EVENING_HOME_CIRCLE");
            }
        }
        Selection best = null;
        if (snapshot != null) {
            for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
                Selection candidate = classify(building);
                if (candidate == null) {
                    continue;
                }
                if (best == null || candidate.priority() > best.priority()) {
                    best = candidate;
                }
            }
            if (best != null) {
                return best;
            }
            for (BannerModSettlementMarketRecord market : snapshot.marketState().markets()) {
                if (market == null || !market.open()) {
                    continue;
                }
                Vec3 marketPos = buildingCenter(snapshot, market.buildingUuid());
                if (marketPos != null) {
                    return new Selection(marketPos, "MARKET_GATHERING", 80);
                }
            }
        }
        Vec3 fallback = snapshot == null || snapshot.buildings().isEmpty() ? null : settlementCenter(snapshot);
        return new Selection(fallback, "STREET_SIDE_CHAT", 1);
    }

    private static @Nullable Selection classify(@Nullable BannerModSettlementBuildingRecord building) {
        if (building == null || building.originPos() == null) {
            return null;
        }
        String typeId = building.buildingTypeId();
        if (typeId == null || typeId.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(typeId);
        String path = (parsed == null ? typeId : parsed.getPath()).toLowerCase();
        Vec3 pos = Vec3.atCenterOf(building.originPos());
        if (path.contains("tavern") || path.contains("inn") || path.contains("pub") || path.contains("alehouse")) {
            return new Selection(pos, "TAVERN_GATHERING", 96);
        }
        if (path.contains("square") || path.contains("plaza") || path.contains("forum")) {
            return new Selection(pos, "SQUARE_GATHERING", 92);
        }
        if (path.contains("hall") || path.contains("meeting") || path.contains("longhouse")) {
            return new Selection(pos, "HALL_GATHERING", 90);
        }
        if (path.contains("campfire") || path.contains("hearth") || path.contains("bonfire") || path.contains("firepit")) {
            return new Selection(pos, "HEARTH_GATHERING", 88);
        }
        if (path.contains("well") || path.contains("fountain")) {
            return new Selection(pos, "WELL_GATHERING", 86);
        }
        if (path.contains("market")) {
            return new Selection(pos, "MARKET_GATHERING", 84);
        }
        return null;
    }

    public static @Nullable Vec3 buildingCenter(@Nullable BannerModSettlementSnapshot snapshot, @Nullable UUID buildingUuid) {
        if (snapshot == null || buildingUuid == null) {
            return null;
        }
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && buildingUuid.equals(building.buildingUuid()) && building.originPos() != null) {
                return Vec3.atCenterOf(building.originPos());
            }
        }
        return null;
    }

    public static Vec3 settlementCenter(@Nullable BannerModSettlementSnapshot snapshot) {
        if (snapshot == null || snapshot.buildings().isEmpty()) {
            return Vec3.ZERO;
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
            if (building == null || building.originPos() == null) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(building.originPos());
            x += center.x;
            y += center.y;
            z += center.z;
            count++;
        }
        if (count <= 0) {
            return Vec3.ZERO;
        }
        return new Vec3(x / count, y / count, z / count);
    }

    public record Selection(@Nullable Vec3 anchorPos, String routeReasonTag, int priority) {
        public Selection(@Nullable Vec3 anchorPos, String routeReasonTag) {
            this(anchorPos, routeReasonTag, 0);
        }
    }
}
