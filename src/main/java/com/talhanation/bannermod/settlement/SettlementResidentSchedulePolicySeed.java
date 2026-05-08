package com.talhanation.bannermod.settlement;

public enum SettlementResidentSchedulePolicySeed {
    VILLAGE_LIFE_FLEX,
    GOVERNANCE_CIVIC,
    LOCAL_LABOR_DAY,
    FLOATING_LABOR_FLEX,
    ORPHANED_LABOR_DAY;

    public static SettlementResidentSchedulePolicySeed fromTagName(String name) {
        try {
            return SettlementResidentSchedulePolicySeed.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return VILLAGE_LIFE_FLEX;
        }
    }
}
