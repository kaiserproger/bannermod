package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;

public record BannerModSettlementResidentRoleProfile(
        BannerModSettlementResidentRole role,
        BannerModSettlementResidentRuntimeRoleState runtimeRoleState,
        BannerModSettlementResidentMode residentMode,
        BannerModSettlementResidentAssignmentState assignmentState,
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

    public static BannerModSettlementResidentRoleProfile fromTag(CompoundTag tag) {
        return new BannerModSettlementResidentRoleProfile(
                BannerModSettlementResidentRole.fromTagName(tag.getString("Role")),
                BannerModSettlementResidentRuntimeRoleState.fromTagName(tag.getString("RuntimeRoleSeed")),
                BannerModSettlementResidentMode.fromTagName(tag.getString("ResidentMode")),
                BannerModSettlementResidentAssignmentState.fromTagName(tag.getString("AssignmentState")),
                tag.getString("ProfileId"),
                tag.getString("GoalDomainId"),
                tag.getBoolean("PrefersLocalBuilding")
        );
    }

    public static BannerModSettlementResidentRoleProfile defaultFor(BannerModSettlementResidentRole role,
                                                                    BannerModSettlementResidentRuntimeRoleState runtimeRoleState,
                                                                    BannerModSettlementResidentMode residentMode,
                                                                    BannerModSettlementResidentAssignmentState assignmentState) {
        return switch (runtimeRoleState) {
            case VILLAGE_LIFE -> new BannerModSettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    "village_life",
                    "village",
                    false
            );
            case GOVERNANCE -> new BannerModSettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    "governance",
                    "governance",
                    false
            );
            case LOCAL_LABOR -> new BannerModSettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    residentMode == BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER
                            ? "projected_local_labor"
                            : "local_labor",
                    "labor",
                    true
            );
            case FLOATING_LABOR -> new BannerModSettlementResidentRoleProfile(
                    role,
                    runtimeRoleState,
                    residentMode,
                    assignmentState,
                    residentMode == BannerModSettlementResidentMode.PROJECTED_CONTROLLED_WORKER
                            ? "projected_floating_labor"
                            : "floating_labor",
                    "labor",
                    false
            );
            case ORPHANED_LABOR_ASSIGNMENT -> new BannerModSettlementResidentRoleProfile(
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
