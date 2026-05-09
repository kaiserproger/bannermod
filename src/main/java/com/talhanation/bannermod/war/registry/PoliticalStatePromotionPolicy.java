package com.talhanation.bannermod.war.registry;

import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementSnapshot;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class PoliticalStatePromotionPolicy {
    private PoliticalStatePromotionPolicy() {
    }

    public static Result evaluate(SettlementSnapshot snapshot) {
        if (snapshot == null) {
            return new Result(false, Set.of("settlement_snapshot"));
        }
        Set<String> present = new LinkedHashSet<>();
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            present.add(normalize(building.buildingTypeId()));
        }

        Set<String> missing = new LinkedHashSet<>();
        if (!containsAny(present, "town_hall", "starter_fort")) {
            missing.add("town_hall_or_starter_fort");
        }
        if (!containsAny(present, "storage", "storage_area")) {
            missing.add("storage");
        }
        if (!containsAny(present, "market", "market_area")) {
            missing.add("market");
        }
        return new Result(missing.isEmpty(), missing);
    }

    private static boolean containsAny(Set<String> present, String... accepted) {
        for (String value : accepted) {
            if (present.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String buildingTypeId) {
        if (buildingTypeId == null || buildingTypeId.isBlank()) {
            return "";
        }
        int namespace = buildingTypeId.indexOf(':');
        String path = namespace >= 0 ? buildingTypeId.substring(namespace + 1) : buildingTypeId;
        return path.toLowerCase(Locale.ROOT);
    }

    public record Result(boolean allowed, Set<String> missingRequirements) {
        public Result {
            missingRequirements = Set.copyOf(missingRequirements == null ? Set.of() : missingRequirements);
        }

        public String denialReason() {
            return allowed ? "" : "infrastructure_insufficient: missing " + String.join(",", missingRequirements);
        }
    }
}
