package com.talhanation.bannermod.settlement.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClaimStrategicEconomySummary(
        UUID claimUuid,
        int anchorChunkX,
        int anchorChunkZ,
        List<ResourceLine> resources,
        boolean shortage,
        boolean degraded,
        boolean unknown
) {
    public ClaimStrategicEconomySummary {
        resources = List.copyOf(resources == null ? List.of() : resources);
    }

    public List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Claim economy " + this.claimUuid + " chunk=" + this.anchorChunkX + "," + this.anchorChunkZ
                + " shortage=" + this.shortage + " degraded=" + this.degraded + " unknown=" + this.unknown);
        for (ResourceLine resource : this.resources) {
            lines.add(resource.resourceId()
                    + " stockpile=" + resource.stockpileHint()
                    + "/" + resource.capacityHint()
                    + " production=" + resource.productionHint()
                    + " consumption=" + resource.consumptionHint()
                    + " shortage=" + resource.shortage()
                    + " degraded=" + resource.degraded()
                    + " unknown=" + resource.unknown());
        }
        return lines;
    }

    public record ResourceLine(
            String resourceId,
            int stockpileHint,
            int capacityHint,
            int productionHint,
            int consumptionHint,
            boolean shortage,
            boolean degraded,
            boolean unknown
    ) {
        public ResourceLine {
            resourceId = resourceId == null ? "" : resourceId;
            stockpileHint = Math.max(0, stockpileHint);
            capacityHint = Math.max(0, capacityHint);
            productionHint = Math.max(0, productionHint);
            consumptionHint = Math.max(0, consumptionHint);
        }
    }
}
