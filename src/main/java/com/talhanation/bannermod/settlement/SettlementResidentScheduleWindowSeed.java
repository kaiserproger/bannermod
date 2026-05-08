package com.talhanation.bannermod.settlement;

public enum SettlementResidentScheduleWindowSeed {
    DAYLIGHT_FLEX(1000, 11000, 12000, 23999),
    LABOR_DAY(1000, 9000, 12000, 23999),
    CIVIC_DAY(500, 11000, 12000, 23999);

    private final int activeStartTick;
    private final int activeEndTick;
    private final int restStartTick;
    private final int restEndTick;

    SettlementResidentScheduleWindowSeed(int activeStartTick,
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

    public static SettlementResidentScheduleWindowSeed defaultFor(SettlementResidentScheduleSeed scheduleSeed,
                                                                           SettlementResidentRuntimeRoleState runtimeRoleState) {
        if (runtimeRoleState == SettlementResidentRuntimeRoleState.GOVERNANCE
                || scheduleSeed == SettlementResidentScheduleSeed.GOVERNING) {
            return CIVIC_DAY;
        }
        if (runtimeRoleState == SettlementResidentRuntimeRoleState.LOCAL_LABOR
                || runtimeRoleState == SettlementResidentRuntimeRoleState.ORPHANED_LABOR_ASSIGNMENT
                || scheduleSeed == SettlementResidentScheduleSeed.ASSIGNED_WORK) {
            return LABOR_DAY;
        }
        return DAYLIGHT_FLEX;
    }

    public static SettlementResidentScheduleWindowSeed fromTagName(String name) {
        try {
            return SettlementResidentScheduleWindowSeed.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return DAYLIGHT_FLEX;
        }
    }
}
