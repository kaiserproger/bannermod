package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;

public record SettlementResidentRecord(
        UUID residentUuid,
        SettlementResidentRole role,
        SettlementResidentScheduleSeed scheduleSeed,
        SettlementResidentScheduleWindowSeed scheduleWindowSeed,
        SettlementResidentRuntimeRoleState runtimeRoleState,
        SettlementResidentServiceContract serviceContract,
        SettlementResidentJobDefinition jobDefinition,
        SettlementResidentJobTargetSelectionState jobTargetSelectionState,
        SettlementResidentMode residentMode,
        @Nullable UUID ownerUuid,
        @Nullable String teamId,
        @Nullable UUID boundWorkAreaUuid,
        SettlementResidentAssignmentState assignmentState,
        SettlementResidentRoleProfile roleProfile,
        SettlementResidentSchedulePolicy schedulePolicy
) {
    public SettlementResidentRecord(UUID residentUuid,
                                             SettlementResidentRole role,
                                             SettlementResidentScheduleSeed scheduleSeed,
                                             SettlementResidentScheduleWindowSeed scheduleWindowSeed,
                                             SettlementResidentRuntimeRoleState runtimeRoleState,
                                             SettlementResidentServiceContract serviceContract,
                                             SettlementResidentJobDefinition jobDefinition,
                                             SettlementResidentJobTargetSelectionState jobTargetSelectionState,
                                             SettlementResidentMode residentMode,
                                             @Nullable UUID ownerUuid,
                                             @Nullable String teamId,
                                             @Nullable UUID boundWorkAreaUuid,
                                             SettlementResidentAssignmentState assignmentState,
                                             SettlementResidentRoleProfile roleProfile) {
        this(
                residentUuid,
                role,
                scheduleSeed,
                scheduleWindowSeed,
                runtimeRoleState,
                serviceContract,
                jobDefinition,
                jobTargetSelectionState,
                residentMode,
                ownerUuid,
                teamId,
                boundWorkAreaUuid,
                assignmentState,
                roleProfile,
                SettlementResidentSchedulePolicy.defaultFor(scheduleSeed, scheduleWindowSeed, runtimeRoleState, roleProfile)
        );
    }

    public SettlementResidentRecord(UUID residentUuid,
                                             SettlementResidentRole role,
                                             SettlementResidentScheduleSeed scheduleSeed,
                                             SettlementResidentScheduleWindowSeed scheduleWindowSeed,
                                             SettlementResidentRuntimeRoleState runtimeRoleState,
                                             SettlementResidentServiceContract serviceContract,
                                             SettlementResidentJobDefinition jobDefinition,
                                             SettlementResidentJobTargetSelectionState jobTargetSelectionState,
                                             SettlementResidentMode residentMode,
                                             @Nullable UUID ownerUuid,
                                             @Nullable String teamId,
                                             @Nullable UUID boundWorkAreaUuid,
                                             SettlementResidentAssignmentState assignmentState) {
        this(
                residentUuid,
                role,
                scheduleSeed,
                scheduleWindowSeed,
                runtimeRoleState,
                serviceContract,
                jobDefinition,
                jobTargetSelectionState,
                residentMode,
                ownerUuid,
                teamId,
                boundWorkAreaUuid,
                assignmentState,
                SettlementResidentRoleProfile.defaultFor(role, runtimeRoleState, residentMode, assignmentState)
        );
    }

    public SettlementResidentRecord(UUID residentUuid,
                                             SettlementResidentRole role,
                                             SettlementResidentScheduleSeed scheduleSeed,
                                             SettlementResidentScheduleWindowSeed scheduleWindowSeed,
                                             SettlementResidentRuntimeRoleState runtimeRoleState,
                                             SettlementResidentServiceContract serviceContract,
                                             SettlementResidentJobDefinition jobDefinition,
                                             SettlementResidentMode residentMode,
                                             @Nullable UUID ownerUuid,
                                             @Nullable String teamId,
                                             @Nullable UUID boundWorkAreaUuid,
                                             SettlementResidentAssignmentState assignmentState) {
        this(
                residentUuid,
                role,
                scheduleSeed,
                scheduleWindowSeed,
                runtimeRoleState,
                serviceContract,
                jobDefinition,
                SettlementResidentJobTargetSelectionState.defaultFor(residentUuid, jobDefinition, serviceContract, SettlementMarketState.empty()),
                residentMode,
                ownerUuid,
                teamId,
                boundWorkAreaUuid,
                assignmentState,
                SettlementResidentRoleProfile.defaultFor(role, runtimeRoleState, residentMode, assignmentState)
        );
    }

    public SettlementResidentRecord(UUID residentUuid,
                                             SettlementResidentRole role,
                                             SettlementResidentScheduleSeed scheduleSeed,
                                             SettlementResidentRuntimeRoleState runtimeRoleState,
                                             SettlementResidentServiceContract serviceContract,
                                             SettlementResidentMode residentMode,
                                             @Nullable UUID ownerUuid,
                                             @Nullable String teamId,
                                             @Nullable UUID boundWorkAreaUuid,
                                             SettlementResidentAssignmentState assignmentState) {
        this(
                residentUuid,
                role,
                scheduleSeed,
                SettlementResidentScheduleWindowSeed.defaultFor(scheduleSeed, runtimeRoleState),
                runtimeRoleState,
                serviceContract,
                SettlementResidentJobDefinition.defaultFor(role, runtimeRoleState, serviceContract, null),
                SettlementResidentJobTargetSelectionState.defaultFor(
                        residentUuid,
                        SettlementResidentJobDefinition.defaultFor(role, runtimeRoleState, serviceContract, null),
                        serviceContract,
                        SettlementMarketState.empty()
                ),
                residentMode,
                ownerUuid,
                teamId,
                boundWorkAreaUuid,
                assignmentState,
                SettlementResidentRoleProfile.defaultFor(role, runtimeRoleState, residentMode, assignmentState)
        );
    }

    public SettlementResidentRecord(UUID residentUuid,
                                             SettlementResidentRole role,
                                             SettlementResidentScheduleSeed scheduleSeed,
                                             SettlementResidentScheduleWindowSeed scheduleWindowSeed,
                                             SettlementResidentRuntimeRoleState runtimeRoleState,
                                             SettlementResidentServiceContract serviceContract,
                                             SettlementResidentMode residentMode,
                                             @Nullable UUID ownerUuid,
                                             @Nullable String teamId,
                                             @Nullable UUID boundWorkAreaUuid,
                                             SettlementResidentAssignmentState assignmentState) {
        this(
                residentUuid,
                role,
                scheduleSeed,
                scheduleWindowSeed,
                runtimeRoleState,
                serviceContract,
                SettlementResidentJobDefinition.defaultFor(role, runtimeRoleState, serviceContract, null),
                SettlementResidentJobTargetSelectionState.defaultFor(
                        residentUuid,
                        SettlementResidentJobDefinition.defaultFor(role, runtimeRoleState, serviceContract, null),
                        serviceContract,
                        SettlementMarketState.empty()
                ),
                residentMode,
                ownerUuid,
                teamId,
                boundWorkAreaUuid,
                assignmentState,
                SettlementResidentRoleProfile.defaultFor(role, runtimeRoleState, residentMode, assignmentState)
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ResidentUuid", this.residentUuid);
        tag.putString("Role", this.role.name());
        tag.putString("ScheduleSeed", this.scheduleSeed.name());
        tag.putString("ScheduleWindowSeed", this.scheduleWindowSeed.name());
        tag.putString("RuntimeRoleSeed", this.runtimeRoleState.name());
        tag.put("ServiceContract", this.serviceContract.toTag());
        tag.put("JobDefinition", this.jobDefinition.toTag());
        tag.put("JobTargetSelectionSeed", this.jobTargetSelectionState.toTag());
        tag.putString("ResidentMode", this.residentMode.name());
        if (this.ownerUuid != null) {
            tag.putUUID("OwnerUuid", this.ownerUuid);
        }
        if (this.teamId != null && !this.teamId.isBlank()) {
            tag.putString("TeamId", this.teamId);
        }
        if (this.boundWorkAreaUuid != null) {
            tag.putUUID("BoundWorkAreaUuid", this.boundWorkAreaUuid);
        }
        tag.putString("AssignmentState", this.assignmentState.name());
        tag.put("RoleProfile", this.roleProfile.toTag());
        tag.put("SchedulePolicy", this.schedulePolicy.toTag());
        return tag;
    }

    public static SettlementResidentRecord fromTag(CompoundTag tag) {
        SettlementResidentRole role = SettlementResidentRole.fromTagName(tag.getString("Role"));
        UUID ownerUuid = tag.hasUUID("OwnerUuid") ? tag.getUUID("OwnerUuid") : null;
        String teamId = tag.contains("TeamId", Tag.TAG_STRING) ? tag.getString("TeamId") : null;
        UUID boundWorkAreaUuid = tag.hasUUID("BoundWorkAreaUuid") ? tag.getUUID("BoundWorkAreaUuid") : null;
        SettlementResidentScheduleSeed scheduleSeed = tag.contains("ScheduleSeed", Tag.TAG_STRING)
                ? scheduleSeedFromTagName(tag.getString("ScheduleSeed"), role, boundWorkAreaUuid)
                : SettlementResidentScheduleSeed.defaultFor(role, boundWorkAreaUuid);
        SettlementResidentMode residentMode = tag.contains("ResidentMode", Tag.TAG_STRING)
                ? SettlementResidentMode.fromTagName(tag.getString("ResidentMode"))
                : SettlementResidentMode.defaultFor(role, ownerUuid);
        SettlementResidentAssignmentState assignmentState = tag.contains("AssignmentState", Tag.TAG_STRING)
                ? SettlementResidentAssignmentState.fromTagName(tag.getString("AssignmentState"))
                : defaultAssignmentState(role, boundWorkAreaUuid);
        SettlementResidentRuntimeRoleState runtimeRoleState = tag.contains("RuntimeRoleSeed", Tag.TAG_STRING)
                ? SettlementResidentRuntimeRoleState.fromTagName(tag.getString("RuntimeRoleSeed"))
                : SettlementResidentRuntimeRoleState.defaultFor(role, scheduleSeed, residentMode, assignmentState);
        SettlementResidentScheduleWindowSeed scheduleWindowSeed = tag.contains("ScheduleWindowSeed", Tag.TAG_STRING)
                ? SettlementResidentScheduleWindowSeed.fromTagName(tag.getString("ScheduleWindowSeed"))
                : SettlementResidentScheduleWindowSeed.defaultFor(scheduleSeed, runtimeRoleState);
        SettlementResidentServiceContract serviceContract = tag.contains("ServiceContract", Tag.TAG_COMPOUND)
                ? SettlementResidentServiceContract.fromTag(tag.getCompound("ServiceContract"))
                : SettlementResidentServiceContract.defaultFor(role, residentMode, assignmentState, boundWorkAreaUuid, null);
        SettlementResidentJobDefinition jobDefinition = tag.contains("JobDefinition", Tag.TAG_COMPOUND)
                ? SettlementResidentJobDefinition.fromTag(tag.getCompound("JobDefinition"))
                : SettlementResidentJobDefinition.defaultFor(role, runtimeRoleState, serviceContract, null);
        SettlementResidentJobTargetSelectionState jobTargetSelectionState = tag.contains("JobTargetSelectionSeed", Tag.TAG_COMPOUND)
                ? SettlementResidentJobTargetSelectionState.fromTag(tag.getCompound("JobTargetSelectionSeed"))
                : SettlementResidentJobTargetSelectionState.defaultFor(tag.getUUID("ResidentUuid"), jobDefinition, serviceContract, SettlementMarketState.empty());
        SettlementResidentRoleProfile roleProfile = tag.contains("RoleProfile", Tag.TAG_COMPOUND)
                ? SettlementResidentRoleProfile.fromTag(tag.getCompound("RoleProfile"))
                : SettlementResidentRoleProfile.defaultFor(role, runtimeRoleState, residentMode, assignmentState);
        SettlementResidentSchedulePolicy schedulePolicy = tag.contains("SchedulePolicy", Tag.TAG_COMPOUND)
                ? SettlementResidentSchedulePolicy.fromTag(tag.getCompound("SchedulePolicy"))
                : SettlementResidentSchedulePolicy.defaultFor(scheduleSeed, scheduleWindowSeed, runtimeRoleState, roleProfile);
        return new SettlementResidentRecord(
                tag.getUUID("ResidentUuid"),
                role,
                scheduleSeed,
                scheduleWindowSeed,
                runtimeRoleState,
                serviceContract,
                jobDefinition,
                jobTargetSelectionState,
                residentMode,
                ownerUuid,
                teamId,
                boundWorkAreaUuid,
                assignmentState,
                roleProfile,
                schedulePolicy
        );
    }

    public @Nullable UUID effectiveWorkBuildingUuid() {
        if (this.serviceContract != null && this.serviceContract.serviceBuildingUuid() != null) {
            return this.serviceContract.serviceBuildingUuid();
        }
        if (this.jobDefinition != null && this.jobDefinition.targetBuildingUuid() != null) {
            return this.jobDefinition.targetBuildingUuid();
        }
        return this.boundWorkAreaUuid;
    }

    private static SettlementResidentAssignmentState defaultAssignmentState(SettlementResidentRole role,
                                                                                      @Nullable UUID boundWorkAreaUuid) {
        if (role != SettlementResidentRole.CONTROLLED_WORKER) {
            return SettlementResidentAssignmentState.NOT_APPLICABLE;
        }
        return boundWorkAreaUuid == null
                ? SettlementResidentAssignmentState.UNASSIGNED
                : SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING;
    }

    private static SettlementResidentScheduleSeed scheduleSeedFromTagName(String name,
                                                                                   SettlementResidentRole role,
                                                                                   @Nullable UUID boundWorkAreaUuid) {
        try {
            return SettlementResidentScheduleSeed.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return SettlementResidentScheduleSeed.defaultFor(role, boundWorkAreaUuid);
        }
    }
}
