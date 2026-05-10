package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.compat.venaterra.VenaterraDepositCategory;
import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.settlement.SettlementStockpileSummary;
import com.talhanation.bannermod.settlement.SettlementSupplySignal;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.BuildingValidationState;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveRecord;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveState;
import com.talhanation.bannermod.war.runtime.EconomicObjectiveTargetKind;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimStrategicEconomySummaryService {
    private static final List<ResourceKey> RESOURCES = List.of(
            ResourceKey.FOOD,
            ResourceKey.IRON,
            ResourceKey.WOOD,
            ResourceKey.STONE,
            ResourceKey.COINS
    );

    private ClaimStrategicEconomySummaryService() {
    }

    public static ClaimStrategicEconomySummary derive(SettlementSnapshot snapshot,
                                                       List<ValidatedBuildingRecord> validatedBuildings,
                                                       @Nullable BannerModTreasuryLedgerSnapshot treasuryLedger) {
        return derive(snapshot, validatedBuildings, treasuryLedger, List.of(), false);
    }

    public static ClaimStrategicEconomySummary derive(SettlementSnapshot snapshot,
                                                       List<ValidatedBuildingRecord> validatedBuildings,
                                                       @Nullable BannerModTreasuryLedgerSnapshot treasuryLedger,
                                                       @Nullable List<StrategicMineSite> mineSites) {
        return derive(snapshot, validatedBuildings, treasuryLedger, mineSites, FortLevelDefinition.MIN_LEVEL);
    }

    public static ClaimStrategicEconomySummary derive(SettlementSnapshot snapshot,
                                                       List<ValidatedBuildingRecord> validatedBuildings,
                                                       @Nullable BannerModTreasuryLedgerSnapshot treasuryLedger,
                                                       @Nullable List<StrategicMineSite> mineSites,
                                                       int fortLevel) {
        return derive(snapshot, validatedBuildings, treasuryLedger, mineSites, true, fortLevel, List.of(), 0L);
    }

    public static ClaimStrategicEconomySummary derive(SettlementSnapshot snapshot,
                                                       List<ValidatedBuildingRecord> validatedBuildings,
                                                       @Nullable BannerModTreasuryLedgerSnapshot treasuryLedger,
                                                       @Nullable List<StrategicMineSite> mineSites,
                                                       int fortLevel,
                                                       @Nullable List<EconomicObjectiveRecord> objectives,
                                                       long gameTime) {
        return derive(snapshot, validatedBuildings, treasuryLedger, mineSites, true, fortLevel, objectives, gameTime);
    }

    private static ClaimStrategicEconomySummary derive(SettlementSnapshot snapshot,
                                                       List<ValidatedBuildingRecord> validatedBuildings,
                                                       @Nullable BannerModTreasuryLedgerSnapshot treasuryLedger,
                                                       @Nullable List<StrategicMineSite> mineSites,
                                                       boolean strategicMineSitesProvided) {
        return derive(snapshot, validatedBuildings, treasuryLedger, mineSites, strategicMineSitesProvided, FortLevelDefinition.MIN_LEVEL, List.of(), 0L);
    }

    private static ClaimStrategicEconomySummary derive(SettlementSnapshot snapshot,
                                                       List<ValidatedBuildingRecord> validatedBuildings,
                                                       @Nullable BannerModTreasuryLedgerSnapshot treasuryLedger,
                                                       @Nullable List<StrategicMineSite> mineSites,
                                                       boolean strategicMineSitesProvided,
                                                       int fortLevel,
                                                       @Nullable List<EconomicObjectiveRecord> objectives,
                                                       long gameTime) {
        Map<ResourceKey, MutableResource> resources = new EnumMap<>(ResourceKey.class);
        for (ResourceKey resource : RESOURCES) {
            resources.put(resource, new MutableResource());
        }

        List<ValidatedBuildingRecord> safeValidatedBuildings = validatedBuildings == null ? List.of() : validatedBuildings;
        List<StrategicMineSite> safeMineSites = mineSites == null ? List.of() : mineSites;
        List<EconomicObjectiveRecord> safeObjectives = objectives == null ? List.of() : objectives;
        Set<UUID> validBuildingIds = new HashSet<>();
        Set<UUID> invalidBuildingIds = new HashSet<>();
        for (ValidatedBuildingRecord record : safeValidatedBuildings) {
            if (record.settlementId().equals(snapshot.claimUuid())) {
                if (record.state() == BuildingValidationState.VALID) {
                    validBuildingIds.add(record.buildingId());
                } else {
                    invalidBuildingIds.add(record.buildingId());
                }
            }
        }

        applyStockpile(resources, snapshot.stockpileSummary());
        applySupplySignals(resources, snapshot);
        applyMarket(resources, snapshot);
        applyTreasury(resources, treasuryLedger);
        applyBuildings(resources, snapshot, safeValidatedBuildings, validBuildingIds, invalidBuildingIds, strategicMineSitesProvided);
        applyMines(resources, snapshot, safeMineSites, mineMaintenance(resources, snapshot));
        applyObjectives(resources, snapshot, safeMineSites, safeObjectives, gameTime);

        boolean anyShortage = false;
        boolean anyDegraded = false;
        boolean anyUnknown = false;
        EconomicObjectiveState objectiveState = aggregateObjectiveState(snapshot, safeObjectives, gameTime);
        List<ClaimStrategicEconomySummary.ResourceLine> lines = RESOURCES.stream()
                .map(resource -> {
                    MutableResource mutable = resources.get(resource);
                    boolean shortage = mutable.shortage || mutable.stockpileHint + mutable.productionHint < mutable.consumptionHint;
                    return new ClaimStrategicEconomySummary.ResourceLine(
                            resource.id,
                            mutable.stockpileHint,
                            mutable.capacityHint,
                            mutable.productionHint,
                            mutable.consumptionHint,
                            shortage,
                            mutable.degraded,
                            mutable.unknown,
                            mutable.objectiveState
                    );
                })
                .toList();
        for (ClaimStrategicEconomySummary.ResourceLine line : lines) {
            anyShortage |= line.shortage();
            anyDegraded |= line.degraded();
            anyUnknown |= line.unknown();
        }
        for (StrategicMineSite site : safeMineSites) {
            if (site != null && site.claimUuid().equals(snapshot.claimUuid()) && site.ownerPoliticalEntityId() != null
                    && resourceForMineCategory(site.resourceCategory()) == null) {
                anyDegraded |= site.degraded();
                anyUnknown |= site.unknown();
            }
        }

        return new ClaimStrategicEconomySummary(
                snapshot.claimUuid(),
                snapshot.anchorChunkX(),
                snapshot.anchorChunkZ(),
                FortLevelDefinition.forLevel(fortLevel),
                lines,
                anyShortage,
                anyDegraded,
                anyUnknown,
                objectiveState
        );
    }

    private static void applyStockpile(Map<ResourceKey, MutableResource> resources, SettlementStockpileSummary stockpileSummary) {
        int sharedCapacityUnits = stockpileSummary.slotCapacity() / 27;
        for (ResourceKey resource : List.of(ResourceKey.FOOD, ResourceKey.IRON, ResourceKey.WOOD, ResourceKey.STONE)) {
            resources.get(resource).capacityHint += sharedCapacityUnits;
        }
        for (String typeId : stockpileSummary.authoredStorageTypeIds()) {
            ResourceKey resource = resourceForGood(typeId);
            if (resource != null) {
                MutableResource mutable = resources.get(resource);
                mutable.stockpileHint += 1;
                mutable.capacityHint += Math.max(1, stockpileSummary.containerCount());
            }
        }
        if (stockpileSummary.storageBuildingCount() > 0 && stockpileSummary.authoredStorageTypeIds().isEmpty()) {
            for (ResourceKey resource : List.of(ResourceKey.FOOD, ResourceKey.IRON, ResourceKey.WOOD, ResourceKey.STONE)) {
                resources.get(resource).unknown = true;
            }
        }
    }

    private static void applySupplySignals(Map<ResourceKey, MutableResource> resources, SettlementSnapshot snapshot) {
        for (SettlementSupplySignal signal : snapshot.supplySignalState().signals()) {
            ResourceKey resource = resourceForGood(signal.goodId());
            if (resource == null) {
                continue;
            }
            MutableResource mutable = resources.get(resource);
            mutable.stockpileHint += signal.coverageUnits();
            mutable.consumptionHint += signal.desiredUnits();
            mutable.shortage |= signal.shortageUnits() > 0;
        }
    }

    private static void applyMarket(Map<ResourceKey, MutableResource> resources, SettlementSnapshot snapshot) {
        MutableResource coins = resources.get(ResourceKey.COINS);
        coins.capacityHint += snapshot.marketState().totalStorageSlots() / 27;
        coins.productionHint += snapshot.marketState().openMarketCount() + snapshot.marketState().readySellerDispatchCount();
        coins.consumptionHint += Math.max(0, snapshot.marketState().marketCount() - snapshot.marketState().openMarketCount());
    }

    private static void applyTreasury(Map<ResourceKey, MutableResource> resources,
                                      @Nullable BannerModTreasuryLedgerSnapshot treasuryLedger) {
        MutableResource coins = resources.get(ResourceKey.COINS);
        if (treasuryLedger == null) {
            coins.unknown = true;
            return;
        }
        coins.stockpileHint += treasuryLedger.treasuryBalance();
        coins.capacityHint += treasuryLedger.treasuryBalance();
        coins.productionHint += treasuryLedger.lastDepositAmount();
        coins.consumptionHint += treasuryLedger.lastArmyUpkeepDebitAmount();
    }

    private static void applyBuildings(Map<ResourceKey, MutableResource> resources,
                                       SettlementSnapshot snapshot,
                                       List<ValidatedBuildingRecord> validatedBuildings,
                                       Set<UUID> validBuildingIds,
                                       Set<UUID> invalidBuildingIds,
                                       boolean suppressGenericMineContribution) {
        resources.get(ResourceKey.FOOD).consumptionHint += snapshot.assignedResidentCount();
        if (snapshot.missingWorkAreaAssignmentCount() > 0 || snapshot.unassignedWorkerCount() > 0) {
            for (MutableResource resource : resources.values()) {
                resource.degraded = true;
            }
        }

        Set<UUID> snapshotBuildingIds = new HashSet<>();
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            snapshotBuildingIds.add(building.buildingUuid());
            if (suppressGenericMineContribution && isMineLike(building.buildingTypeId())) {
                continue;
            }
            BuildingContribution contribution = contributionForBuilding(building);
            if (contribution.resource() == null) {
                continue;
            }
            MutableResource resource = resources.get(contribution.resource());
            boolean valid = validBuildingIds.contains(building.buildingUuid());
            boolean invalid = invalidBuildingIds.contains(building.buildingUuid());
            boolean staffed = building.assignedWorkerCount() > 0;
            if (valid && staffed) {
                resource.productionHint += Math.max(1, building.assignedWorkerCount()) * contribution.productionUnits();
            } else if (valid) {
                resource.degraded = true;
                resource.productionHint += contribution.degradedUnits();
            } else {
                resource.degraded = true;
                resource.unknown = true;
                resource.productionHint += contribution.degradedUnits();
                if (invalid) {
                    resource.consumptionHint += 1;
                }
            }
            for (Map.Entry<ResourceKey, Integer> consumption : contribution.consumption().entrySet()) {
                resources.get(consumption.getKey()).consumptionHint += consumption.getValue();
            }
        }

        for (ValidatedBuildingRecord record : validatedBuildings) {
            if (!record.settlementId().equals(snapshot.claimUuid()) || snapshotBuildingIds.contains(record.buildingId())) {
                continue;
            }
            if (suppressGenericMineContribution && record.type() == BuildingType.MINE) {
                continue;
            }
            BuildingContribution contribution = contributionForValidatedRecord(record);
            if (contribution.resource() == null) {
                if (record.state() == BuildingValidationState.VALID && record.type() == BuildingType.STORAGE) {
                    for (ResourceKey resource : List.of(ResourceKey.FOOD, ResourceKey.IRON, ResourceKey.WOOD, ResourceKey.STONE)) {
                        MutableResource mutable = resources.get(resource);
                        mutable.capacityHint += Math.max(1, record.capacity());
                        mutable.degraded = true;
                        mutable.unknown = true;
                    }
                }
                continue;
            }
            MutableResource resource = resources.get(contribution.resource());
            resource.productionHint += contribution.degradedUnits();
            resource.degraded = true;
            resource.unknown = true;
        }
    }

    private static void applyMines(Map<ResourceKey, MutableResource> resources,
                                   SettlementSnapshot snapshot,
                                   List<StrategicMineSite> mineSites,
                                   MineMaintenance maintenance) {
        for (StrategicMineSite site : mineSites) {
            if (site == null || !site.claimUuid().equals(snapshot.claimUuid()) || site.ownerPoliticalEntityId() == null) {
                continue;
            }
            ResourceKey resource = resourceForMineCategory(site.resourceCategory());
            if (resource == null) {
                continue;
            }

            MutableResource mutable = resources.get(resource);
            boolean lowEfficiency = site.degraded() || site.assignedWorkerCount() <= 0 || maintenance.missingInputs();
            boolean degraded = lowEfficiency || maintenance.disruptionUnits() > 0;
            if (degraded) {
                mutable.degraded = true;
            }
            if (site.unknown()) {
                mutable.unknown = true;
                continue;
            }
            mutable.productionHint += mineProductionHint(site, lowEfficiency, maintenance.disruptionUnits());
        }
    }

    private static MineMaintenance mineMaintenance(Map<ResourceKey, MutableResource> resources, SettlementSnapshot snapshot) {
        boolean missingFood = resources.get(ResourceKey.FOOD).shortage;
        boolean missingWood = resources.get(ResourceKey.WOOD).shortage;
        boolean missingTools = false;
        int disruptionUnits = 0;
        for (SettlementSupplySignal signal : snapshot.supplySignalState().signals()) {
            String goodId = signal.goodId().toLowerCase(Locale.ROOT);
            missingTools |= goodId.contains("tool") && signal.shortageUnits() > 0;
            if (goodId.contains("mine_disruption") || goodId.contains("disruption")) {
                disruptionUnits += signal.reservationHintUnits();
            }
        }
        return new MineMaintenance(missingFood || missingWood || missingTools, disruptionUnits);
    }

    private static void applyObjectives(Map<ResourceKey, MutableResource> resources,
                                        SettlementSnapshot snapshot,
                                        List<StrategicMineSite> mineSites,
                                        List<EconomicObjectiveRecord> objectives,
                                        long gameTime) {
        for (EconomicObjectiveRecord objective : objectives) {
            if (objective == null || !snapshot.claimUuid().equals(objective.claimUuid()) || !objective.isActiveAt(gameTime)) {
                continue;
            }
            EconomicObjectiveState state = objective.economyStateAt(gameTime);
            if (objective.targetKind() == EconomicObjectiveTargetKind.MINE) {
                applyMineObjective(resources, mineSites, objective, state);
            } else if (objective.targetKind() == EconomicObjectiveTargetKind.ROUTE
                    || objective.targetKind() == EconomicObjectiveTargetKind.STORAGE) {
                for (MutableResource resource : resources.values()) {
                    resource.objectiveState = resource.objectiveState.merge(state);
                }
            }
        }
    }

    private static EconomicObjectiveState aggregateObjectiveState(SettlementSnapshot snapshot,
                                                                  List<EconomicObjectiveRecord> objectives,
                                                                  long gameTime) {
        EconomicObjectiveState state = EconomicObjectiveState.NORMAL;
        for (EconomicObjectiveRecord objective : objectives) {
            if (objective != null && snapshot.claimUuid().equals(objective.claimUuid()) && objective.isActiveAt(gameTime)) {
                state = state.merge(objective.economyStateAt(gameTime));
            }
        }
        return state;
    }

    private static void applyMineObjective(Map<ResourceKey, MutableResource> resources,
                                           List<StrategicMineSite> mineSites,
                                           EconomicObjectiveRecord objective,
                                           EconomicObjectiveState state) {
        UUID siteId = objective.strategicObjectId();
        if (siteId == null) {
            return;
        }
        for (StrategicMineSite site : mineSites) {
            if (site == null || !siteId.equals(site.siteId())) {
                continue;
            }
            ResourceKey resource = resourceForMineCategory(site.resourceCategory());
            if (resource != null) {
                MutableResource mutable = resources.get(resource);
                mutable.objectiveState = mutable.objectiveState.merge(state);
            }
            return;
        }
    }

    private static BuildingContribution contributionForBuilding(SettlementBuildingRecord building) {
        String typeId = building.buildingTypeId() == null ? "" : building.buildingTypeId().toLowerCase(Locale.ROOT);
        if (typeId.contains("farm") || typeId.contains("crop") || typeId.contains("animal_pen") || typeId.contains("fishing")) {
            return new BuildingContribution(ResourceKey.FOOD, 2, 1, Map.of());
        }
        if (typeId.contains("lumber")) {
            return new BuildingContribution(ResourceKey.WOOD, 2, 1, Map.of());
        }
        if (typeId.contains("mine") || typeId.contains("mining_area")) {
            return new BuildingContribution(ResourceKey.IRON, 1, 1, Map.of(ResourceKey.WOOD, 1));
        }
        if (typeId.contains("smith")) {
            return new BuildingContribution(ResourceKey.IRON, 1, 1, Map.of(ResourceKey.WOOD, 1));
        }
        if (typeId.contains("architect") || typeId.contains("build_area")) {
            return new BuildingContribution(ResourceKey.STONE, 1, 1, Map.of(ResourceKey.WOOD, 1));
        }
        return new BuildingContribution(null, 0, 0, Map.of());
    }

    private static boolean isMineLike(@Nullable String buildingTypeId) {
        if (buildingTypeId == null || buildingTypeId.isBlank()) {
            return false;
        }
        String normalized = buildingTypeId.toLowerCase(Locale.ROOT);
        return normalized.contains("mine") || normalized.contains("mining_area");
    }

    private static BuildingContribution contributionForValidatedRecord(ValidatedBuildingRecord record) {
        return switch (record.type()) {
            case FARM -> new BuildingContribution(ResourceKey.FOOD, 2, 1, Map.of());
            case LUMBER_CAMP -> new BuildingContribution(ResourceKey.WOOD, 2, 1, Map.of());
            case MINE -> new BuildingContribution(ResourceKey.IRON, 1, 1, Map.of(ResourceKey.WOOD, 1));
            case SMITHY -> new BuildingContribution(ResourceKey.IRON, 1, 1, Map.of(ResourceKey.WOOD, 1));
            case ARCHITECT_WORKSHOP -> new BuildingContribution(ResourceKey.STONE, 1, 1, Map.of(ResourceKey.WOOD, 1));
            default -> new BuildingContribution(null, 0, 0, Map.of());
        };
    }

    private static ResourceKey resourceForGood(String goodId) {
        if (goodId == null || goodId.isBlank()) {
            return null;
        }
        String normalized = goodId.toLowerCase(Locale.ROOT);
        if (normalized.contains("food")) {
            return ResourceKey.FOOD;
        }
        if (normalized.contains("iron")) {
            return ResourceKey.IRON;
        }
        if (normalized.contains("tool")) {
            return ResourceKey.IRON;
        }
        if (normalized.contains("wood") || normalized.contains("lumber")) {
            return ResourceKey.WOOD;
        }
        if (normalized.contains("stone") || normalized.contains("construction_material")) {
            return ResourceKey.STONE;
        }
        if (normalized.contains("coin") || normalized.contains("tax") || normalized.contains("market")) {
            return ResourceKey.COINS;
        }
        return null;
    }

    @Nullable
    private static ResourceKey resourceForMineCategory(VenaterraDepositCategory category) {
        return switch (category) {
            case IRON -> ResourceKey.IRON;
            case QUARRY_STONE -> ResourceKey.STONE;
            case PRECIOUS_COIN_VALUE -> ResourceKey.COINS;
            default -> null;
        };
    }

    private static int mineProductionHint(StrategicMineSite site, boolean lowEfficiency, int disruptionUnits) {
        int richnessYield = (int) Math.floor(site.richness() * 3.0F);
        int baseYield = Math.max(1, Math.min(3, richnessYield));
        if (lowEfficiency) {
            baseYield = Math.min(baseYield, 1);
        }
        return Math.max(0, baseYield - disruptionUnits);
    }

    private record MineMaintenance(boolean missingInputs, int disruptionUnits) {
    }

    private enum ResourceKey {
        FOOD("food"),
        IRON("iron"),
        WOOD("wood"),
        STONE("stone"),
        COINS("coins");

        private final String id;

        ResourceKey(String id) {
            this.id = id;
        }
    }

    private static final class MutableResource {
        private int stockpileHint;
        private int capacityHint;
        private int productionHint;
        private int consumptionHint;
        private boolean shortage;
        private boolean degraded;
        private boolean unknown;
        private EconomicObjectiveState objectiveState = EconomicObjectiveState.NORMAL;
    }

    private record BuildingContribution(@Nullable ResourceKey resource,
                                        int productionUnits,
                                        int degradedUnits,
                                        Map<ResourceKey, Integer> consumption) {
    }
}
