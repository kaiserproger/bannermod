package com.talhanation.bannermod.settlement;

public enum SettlementJobTargetSelectionMode {
    NONE,
    SERVICE_BUILDING,
    SELLER_MARKET_DISPATCH,
    SELLER_MARKET_CLOSED,
    FLOATING_LABOR_POOL,
    ORPHANED_SERVICE_BUILDING;

    public static SettlementJobTargetSelectionMode fromTagName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
