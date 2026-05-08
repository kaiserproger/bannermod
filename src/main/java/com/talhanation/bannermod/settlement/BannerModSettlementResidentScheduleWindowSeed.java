package com.talhanation.bannermod.settlement;

public enum BannerModSettlementResidentScheduleWindowSeed {
    DAYLIGHT_FLEX(1000, 11000, 12000, 23999),
    LABOR_DAY(1000, 9000, 12000, 23999),
    CIVIC_DAY(500, 11000, 12000, 23999);

    private final int activeStartTick;
    private final int activeEndTick;
    private final int restStartTick;
    private final int restEndTick;

    BannerModSettlementResidentScheduleWindowSeed(int activeStartTick,
                                                  int activeEndTick,
                                                  int restStartTick,
                                                  int restEndTick) {
        this.activeStartTick = activeStartTick;
        this.activeEndTick = activeEndTick;
        this.restStartTick = restStartTick;
        this.restEndTick = restEndTick;
    }

    public int activeStartTick() {
        return this.activeStartTick;
    }

    public int activeEndTick() {
        return this.activeEndTick;
    }

    public int restStartTick() {
        return this.restStartTick;
    }

    public int restEndTick() {
        return this.restEndTick;
    }

    public static BannerModSettlementResidentScheduleWindowSeed defaultFor(BannerModSettlementResidentScheduleSeed scheduleSeed,
                                                                           BannerModSettlementResidentRuntimeRoleState runtimeRoleState) {
        if (runtimeRoleState == BannerModSettlementResidentRuntimeRoleState.GOVERNANCE
                || scheduleSeed == BannerModSettlementResidentScheduleSeed.GOVERNING) {
            return CIVIC_DAY;
        }
        if (runtimeRoleState == BannerModSettlementResidentRuntimeRoleState.LOCAL_LABOR
                || runtimeRoleState == BannerModSettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT
                || scheduleSeed == BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK) {
            return LABOR_DAY;
        }
        return DAYLIGHT_FLEX;
    }

    public static BannerModSettlementResidentScheduleWindowSeed fromTagName(String name) {
        try {
            return BannerModSettlementResidentScheduleWindowSeed.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return DAYLIGHT_FLEX;
        }
    }
}
