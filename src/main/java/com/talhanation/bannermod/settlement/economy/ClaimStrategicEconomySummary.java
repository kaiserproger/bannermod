package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.war.runtime.EconomicObjectiveState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClaimStrategicEconomySummary(
        UUID claimUuid,
        int anchorChunkX,
        int anchorChunkZ,
        FortLevelDefinition fortLevel,
        List<ResourceLine> resources,
        boolean shortage,
        boolean degraded,
        boolean unknown,
        EconomicObjectiveState objectiveState
) {
    public ClaimStrategicEconomySummary {
        fortLevel = fortLevel == null ? FortLevelDefinition.forLevel(FortLevelDefinition.MIN_LEVEL) : fortLevel;
        resources = List.copyOf(resources == null ? List.of() : resources);
        objectiveState = objectiveState == null ? EconomicObjectiveState.NORMAL : objectiveState;
    }

    public ClaimStrategicEconomySummary(UUID claimUuid,
                                        int anchorChunkX,
                                        int anchorChunkZ,
                                        FortLevelDefinition fortLevel,
                                        List<ResourceLine> resources,
                                        boolean shortage,
                                        boolean degraded,
                                        boolean unknown) {
        this(claimUuid, anchorChunkX, anchorChunkZ, fortLevel, resources, shortage, degraded, unknown, EconomicObjectiveState.NORMAL);
    }

    public List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Claim economy " + this.claimUuid + " chunk=" + this.anchorChunkX + "," + this.anchorChunkZ
                + " shortage=" + this.shortage + " degraded=" + this.degraded + " unknown=" + this.unknown
                + " objective=" + this.objectiveState);
        lines.add("Fort level " + this.fortLevel.level() + " " + this.fortLevel.name()
                + " caps workers=" + this.fortLevel.workerCap()
                + " soldiers=" + this.fortLevel.soldierCap()
                + " mines=" + this.fortLevel.mineCap()
                + " outposts=" + this.fortLevel.outpostCap());
        FortLevelDefinition.UpgradeRequirement requirement = this.fortLevel.nextLevelRequirement();
        lines.add(requirement == null
                ? "Next fort level requirements: max level"
                : "Next fort level requirements: " + requirement.debugString());
        for (ResourceLine resource : this.resources) {
            lines.add(resource.resourceId()
                    + " stockpile=" + resource.stockpileHint()
                    + "/" + resource.capacityHint()
                    + " production=" + resource.productionHint()
                    + " consumption=" + resource.consumptionHint()
                    + " shortage=" + resource.shortage()
                    + " degraded=" + resource.degraded()
                    + " unknown=" + resource.unknown()
                    + " objective=" + resource.objectiveState());
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
            boolean unknown,
            EconomicObjectiveState objectiveState
    ) {
        public ResourceLine {
            resourceId = resourceId == null ? "" : resourceId;
            stockpileHint = Math.max(0, stockpileHint);
            capacityHint = Math.max(0, capacityHint);
            productionHint = Math.max(0, productionHint);
            consumptionHint = Math.max(0, consumptionHint);
            objectiveState = objectiveState == null ? EconomicObjectiveState.NORMAL : objectiveState;
        }

        public ResourceLine(String resourceId,
                            int stockpileHint,
                            int capacityHint,
                            int productionHint,
                            int consumptionHint,
                            boolean shortage,
                            boolean degraded,
                            boolean unknown) {
            this(resourceId, stockpileHint, capacityHint, productionHint, consumptionHint, shortage, degraded, unknown, EconomicObjectiveState.NORMAL);
        }
    }
}
