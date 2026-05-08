package com.talhanation.bannermod.settlement;

public enum SettlementBuildingCategory {
    FOOD,
    MATERIAL,
    STORAGE,
    MARKET,
    CONSTRUCTION,
    GENERAL;

    public static SettlementBuildingCategory fromTagName(String name) {
        if (name == null || name.isBlank()) {
            return GENERAL;
        }
        try {
            return SettlementBuildingCategory.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return GENERAL;
        }
    }
}
