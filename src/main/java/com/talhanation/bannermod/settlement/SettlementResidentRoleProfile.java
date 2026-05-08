package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;

public record SettlementResidentRoleProfile(
        SettlementResidentRole role,
        SettlementResidentRuntimeRoleState runtimeRoleState,
        SettlementResidentMode residentMode,
        SettlementResidentAssignmentState assignmentState,
        String profileId,
        String goalDomainId,
        boolean prefersLocalBuilding
) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Role", this.role.name());
        tag.putString("RuntimeRoleSeed", this.runtimeRoleState.name());
        tag.putString("ResidentMode", this.residentMode.name());
        tag.putString("AssignmentState", this.assignmentState.name());
        tag.putString("ProfileId", this.profileId);
        tag.putString("GoalDomainId", this.goalDomainId);
        tag.putBoolean("PrefersLocalBuilding", this.prefersLocalBuilding);
        return tag;
    }

    public static SettlementResidentRoleProfile fromTag(CompoundTag tag) {
        return new SettlementResidentRoleProfile(
                SettlementResidentRole.fromTagName(tag.getString("Role")),
                SettlementResidentRuntimeRoleState.fromTagName(tag.getString("RuntimeRoleSeed")),
                SettlementResidentMode.fromTagName(tag.getString("ResidentMode")),
                SettlementResidentAssignmentState.fromTagName(tag.getString("AssignmentState")),
                tag.getString("ProfileId"),
                tag.getString("GoalDomainId"),
                tag.getBoolean("PrefersLocalBuilding")
        );
    }

    public static SettlementResidentRoleProfile defaultFor(SettlementResidentRole role,
                                                                    SettlementResidentRuntimeRoleState runtimeRoleState,
                                                                    SettlementResidentMode residentMode,
                                                                    SettlementResidentAssignmentState assignmentState) {
        return switch (runtimeRoleState) {
            case VILLAGE_LIFE -> new SettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    "village_life",
                    "village",
                    false
            );
            case GOVERNANCE -> new SettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    "governance",
                    "governance",
                    false
            );
            case LOCAL_LABOR -> new SettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    residentMode == SettlementResidentMode.PROJECTED_CONTROLLED_WORKER
                            ? "projected_local_labor"
                            : "local_labor",
                    "labor",
                    true
            );
            case FLOATING_LABOR -> new SettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    residentMode == SettlementResidentMode.PROJECTED_CONTROLLED_WORKER
                            ? "projected_floating_labor"
                            : "floating_labor",
                    "labor",
                    false
            );
            case ORPHANED_LABOR_ASSIGNMENT -> new SettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    "orphaned_labor_assignment",
                    "labor",
                    false
            );
        };
    }
}
