package com.talhanation.bannermod.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record SettlementStrategicSignals(
        String roleId,
        String roleDescription,
        String routeCostId,
        String routeCostDescription,
        String specializationId,
        String specializationDescription,
        List<String> logisticsObjectiveIds,
        List<String> loyaltyPressureIds
) {
    public SettlementStrategicSignals {
        roleId = normalize(roleId, "outpost");
        roleDescription = blankToDefault(roleDescription, "Local outpost with no dominant logistics role yet.");
        routeCostId = normalize(routeCostId, "isolated");
        routeCostDescription = blankToDefault(routeCostDescription, "Supply depends on local stores or manual hauling.");
        specializationId = normalize(specializationId, "none");
        specializationDescription = blankToDefault(specializationDescription, "No landlocked specialty is visible yet.");
        logisticsObjectiveIds = List.copyOf(logisticsObjectiveIds == null ? List.of() : logisticsObjectiveIds);
        loyaltyPressureIds = List.copyOf(loyaltyPressureIds == null ? List.of() : loyaltyPressureIds);
    }

    public static SettlementStrategicSignals fromSnapshot(SettlementSnapshot snapshot) {
        if (snapshot == null) {
            return empty();
        }

        int foodBuildings = countProfile(snapshot, SettlementBuildingProfileSeed.FOOD_PRODUCTION);
        int materialBuildings = countProfile(snapshot, SettlementBuildingProfileSeed.MATERIAL_PRODUCTION);
        int storageCount = snapshot.stockpileSummary().storageBuildingCount();
        int routedStorageCount = snapshot.stockpileSummary().routedStorageCount();
        int portCount = snapshot.stockpileSummary().portEntrypointCount();
        int marketCount = snapshot.marketState().marketCount();
        boolean hasFort = hasBuildingPath(snapshot, "starter_fort") || hasBuildingPath(snapshot, "town_hall");

        String roleId;
        String roleDescription;
        if (portCount > 0) {
            roleId = "water_gate";
            roleDescription = "Water access makes this settlement cheap to supply and valuable to control.";
        } else if (marketCount > 0 && routedStorageCount > 0) {
            roleId = "junction_market";
            roleDescription = "Market and authored routes make this a trade junction.";
        } else if (hasFort && routedStorageCount > 0) {
            roleId = "chokepoint_fort";
            roleDescription = "Fortified route storage makes this a practical chokepoint.";
        } else if (foodBuildings > 0 && storageCount > 0) {
            roleId = "surplus_hub";
            roleDescription = "Food production and storage make this settlement a surplus hub.";
        } else {
            roleId = "local_outpost";
            roleDescription = "Local buildings matter, but no strong logistics role is visible yet.";
        }

        String routeCostId;
        String routeCostDescription;
        if (portCount > 0) {
            routeCostId = "water_advantaged";
            routeCostDescription = "Water or port access gives the cheapest supply path.";
        } else if (routedStorageCount > 1) {
            routeCostId = "connected";
            routeCostDescription = "Multiple authored routes make supply reasonably reliable.";
        } else if (routedStorageCount == 1) {
            routeCostId = "single_route";
            routeCostDescription = "One authored route exists, but disruption can isolate the settlement.";
        } else {
            routeCostId = "landlocked";
            routeCostDescription = "No water or authored route advantage is visible; supply is expensive.";
        }

        String specializationId = "none";
        String specializationDescription = "No landlocked specialty is visible yet.";
        if (portCount == 0 && routedStorageCount == 0 && foodBuildings > 0 && storageCount > 0) {
            specializationId = "preserved_food";
            specializationDescription = "Landlocked farms and storage suggest preserved food for local trade.";
        } else if (portCount == 0 && routedStorageCount == 0 && materialBuildings > 0 && storageCount > 0) {
            specializationId = "worked_materials";
            specializationDescription = "Landlocked material production suggests worked goods over bulk hauling.";
        }

        List<String> objectives = new ArrayList<>();
        if (storageCount > 0) {
            objectives.add("stockpile");
        }
        if (routedStorageCount > 0) {
            objectives.add("route_junction");
        }
        if (portCount > 0) {
            objectives.add("water_gate");
        }
        if (foodBuildings > 0 && storageCount > 0) {
            objectives.add("surplus_store");
        }

        List<String> pressures = new ArrayList<>();
        if ("landlocked".equals(routeCostId)) {
            pressures.add("isolated_supply");
        }
        if ("single_route".equals(routeCostId)) {
            pressures.add("dependent_on_single_route");
        }
        if (marketCount == 0 && storageCount == 0) {
            pressures.add("no_local_distribution");
        }

        return new SettlementStrategicSignals(roleId, roleDescription, routeCostId, routeCostDescription, specializationId, specializationDescription, objectives, pressures);
    }

    public static SettlementStrategicSignals empty() {
        return new SettlementStrategicSignals("outpost", "No settlement logistics snapshot is available.", "unknown", "Route cost is unknown.", "none", "No specialization is visible.", List.of(), List.of());
    }

    private static int countProfile(SettlementSnapshot snapshot, SettlementBuildingProfileSeed profileSeed) {
        int count = 0;
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (building.buildingProfileSeed() == profileSeed) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasBuildingPath(SettlementSnapshot snapshot, String path) {
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            String typeId = building.buildingTypeId();
            if (typeId != null && typeId.toLowerCase(Locale.ROOT).endsWith(path)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
