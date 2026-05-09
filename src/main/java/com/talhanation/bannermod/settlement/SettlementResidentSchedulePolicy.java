package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record SettlementResidentSchedulePolicy(
        SettlementResidentSchedulePolicySeed policySeed,
        SettlementResidentScheduleSeed scheduleSeed,
        SettlementResidentScheduleWindowSeed scheduleWindowSeed,
        String goalDomainId,
        boolean prefersLocalBuilding
) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PolicySeed", this.policySeed.name());
        tag.putString("ScheduleSeed", this.scheduleSeed.name());
        tag.putString("ScheduleWindowSeed", this.scheduleWindowSeed.name());
        tag.putString("GoalDomainId", this.goalDomainId);
        tag.putBoolean("PrefersLocalBuilding", this.prefersLocalBuilding);
        return tag;
    }

    public static SettlementResidentSchedulePolicy fromTag(CompoundTag tag) {
        SettlementResidentSchedulePolicySeed policySeed = tag.contains("PolicySeed", Tag.TAG_STRING)
                ? SettlementResidentSchedulePolicySeed.fromTagName(tag.getString("PolicySeed"))
                : SettlementResidentSchedulePolicySeed.VILLAGE_LIFE_FLEX;
        SettlementResidentScheduleSeed scheduleSeed = tag.contains("ScheduleSeed", Tag.TAG_STRING)
                ? scheduleSeedFromTagName(tag.getString("ScheduleSeed"))
                : SettlementResidentScheduleSeed.SETTLEMENT_IDLE;
        SettlementResidentScheduleWindowSeed scheduleWindowSeed = tag.contains("ScheduleWindowSeed", Tag.TAG_STRING)
                ? SettlementResidentScheduleWindowSeed.fromTagName(tag.getString("ScheduleWindowSeed"))
                : SettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX;
        String goalDomainId = tag.contains("GoalDomainId", Tag.TAG_STRING)
                ? tag.getString("GoalDomainId")
                : "village";
        return new SettlementResidentSchedulePolicy(
                policySeed,
                scheduleSeed,
                scheduleWindowSeed,
                goalDomainId,
                tag.getBoolean("PrefersLocalBuilding")
        );
    }

    public static SettlementResidentSchedulePolicy defaultFor(SettlementResidentScheduleSeed scheduleSeed,
                                                                       SettlementResidentScheduleWindowSeed scheduleWindowSeed,
                                                                       SettlementResidentRuntimeRoleState runtimeRoleState,
                                                                       SettlementResidentRoleProfile roleProfile) {
        return new SettlementResidentSchedulePolicy(
                defaultPolicySeed(scheduleSeed, scheduleWindowSeed, runtimeRoleState),
                scheduleSeed,
                scheduleWindowSeed,
                roleProfile.goalDomainId(),
                roleProfile.prefersLocalBuilding()
        );
    }

    private static SettlementResidentSchedulePolicySeed defaultPolicySeed(SettlementResidentScheduleSeed scheduleSeed,
                                                                                   SettlementResidentScheduleWindowSeed scheduleWindowSeed,
                                                                                   SettlementResidentRuntimeRoleState runtimeRoleState) {
        return switch (runtimeRoleState) {
            case GOVERNANCE -> SettlementResidentSchedulePolicySeed.GOVERNANCE_CIVIC;
            case LOCAL_LABOR -> SettlementResidentSchedulePolicySeed.LOCAL_LABOR_DAY;
            case FLOATING_LABOR -> scheduleWindowSeed == SettlementResidentScheduleWindowSeed.LABOR_DAY
                    || scheduleSeed == SettlementResidentScheduleSeed.ASSIGNED_WORK
                    ? SettlementResidentSchedulePolicySeed.LOCAL_LABOR_DAY
                    : SettlementResidentSchedulePolicySeed.FLOATING_LABOR_FLEX;
            case ORPHANED_LABOR_ASSIGNMENT -> SettlementResidentSchedulePolicySeed.ORPHANED_LABOR_DAY;
            case VILLAGE_LIFE -> scheduleWindowSeed == SettlementResidentScheduleWindowSeed.CIVIC_DAY
                    || scheduleSeed == SettlementResidentScheduleSeed.GOVERNING
                    ? SettlementResidentSchedulePolicySeed.GOVERNANCE_CIVIC
                    : SettlementResidentSchedulePolicySeed.VILLAGE_LIFE_FLEX;
        };
    }

    private static SettlementResidentScheduleSeed scheduleSeedFromTagName(String name) {
        try {
            return SettlementResidentScheduleSeed.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return SettlementResidentScheduleSeed.SETTLEMENT_IDLE;
        }
    }
}
