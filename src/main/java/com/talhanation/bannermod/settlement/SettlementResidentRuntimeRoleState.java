package com.talhanation.bannermod.settlement;

public enum SettlementResidentRuntimeRoleState {
    VILLAGE_LIFE,
    GOVERNANCE,
    LOCAL_LABOR,
    FLOATING_LABOR,
    ORPHANED_LABOR_ASSIGNMENT;

    public static SettlementResidentRuntimeRoleState fromTagName(String name) {
        if (name == null || name.isBlank()) {
            return VILLAGE_LIFE;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return VILLAGE_LIFE;
        }
    }

    public static SettlementResidentRuntimeRoleState defaultFor(SettlementResidentRole role,
                                                                        SettlementResidentScheduleSeed scheduleSeed,
                                                                        SettlementResidentMode residentMode,
                                                                        SettlementResidentAssignmentState assignmentState) {
        return switch (role) {
            case GOVERNOR_RECRUIT -> GOVERNANCE;
            case VILLAGER -> VILLAGE_LIFE;
            case CONTROLLED_WORKER -> defaultWorkerState(scheduleSeed, residentMode, assignmentState);
        };
    }

    private static SettlementResidentRuntimeRoleState defaultWorkerState(SettlementResidentScheduleSeed scheduleSeed,
                                                                                 SettlementResidentMode residentMode,
                                                                                 SettlementResidentAssignmentState assignmentState) {
        if (assignmentState == SettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING) {
            return ORPHANED_LABOR_ASSIGNMENT;
        }
        if (assignmentState == SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                || scheduleSeed == SettlementResidentScheduleSeed.ASSIGNED_WORK) {
            return LOCAL_LABOR;
        }
        if (residentMode == SettlementResidentMode.PROJECTED_CONTROLLED_WORKER) {
            return FLOATING_LABOR;
        }
        return FLOATING_LABOR;
    }
}
