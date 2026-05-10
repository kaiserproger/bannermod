package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.compat.venaterra.VenaterraDepositCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

public record StrategicMineSite(
        UUID siteId,
        UUID claimUuid,
        @Nullable UUID ownerPoliticalEntityId,
        ResourceKey<Level> dimension,
        BlockPos center,
        int radius,
        SourceType sourceType,
        VenaterraDepositCategory resourceCategory,
        float richness,
        boolean degraded,
        boolean unknown
) {
    public StrategicMineSite {
        siteId = siteId == null ? UUID.randomUUID() : siteId;
        claimUuid = claimUuid == null ? new UUID(0L, 0L) : claimUuid;
        dimension = dimension == null ? Level.OVERWORLD : dimension;
        center = center == null ? BlockPos.ZERO : center.immutable();
        radius = Math.max(1, radius);
        sourceType = sourceType == null ? SourceType.CLAIM_MINE_WORK_AREA : sourceType;
        resourceCategory = resourceCategory == null ? VenaterraDepositCategory.UNKNOWN_OTHER : resourceCategory;
        richness = Math.max(0.0F, richness);
    }

    public String debugLine() {
        return "Mine site " + this.siteId
                + " claim=" + this.claimUuid
                + " owner=" + (this.ownerPoliticalEntityId == null ? "unowned" : this.ownerPoliticalEntityId)
                + " dimension=" + this.dimension.location()
                + " center=" + this.center.getX() + "," + this.center.getY() + "," + this.center.getZ()
                + " radius=" + this.radius
                + " source=" + this.sourceType
                + " resource=" + this.resourceCategory
                + " richness=" + this.richness
                + " degraded=" + this.degraded
                + " unknown=" + this.unknown;
    }

    public enum SourceType {
        VALIDATED_MINE_BUILDING,
        CLAIM_MINE_WORK_AREA
    }
}
