package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record BannerModSettlementResidentSchedulePolicy(
        BannerModSettlementResidentSchedulePolicySeed policySeed,
        BannerModSettlementResidentScheduleSeed scheduleSeed,
        BannerModSettlementResidentScheduleWindowSeed scheduleWindowSeed,
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

    public static BannerModSettlementResidentSchedulePolicy fromTag(CompoundTag tag) {
        BannerModSettlementResidentSchedulePolicySeed policySeed = tag.contains("PolicySeed", Tag.TAG_STRING)
                ? BannerModSettlementResidentSchedulePolicySeed.fromTagName(tag.getString("PolicySeed"))
                : BannerModSettlementResidentSchedulePolicySeed.VILLAGE_LIFE_FLEX;
        BannerModSettlementResidentScheduleSeed scheduleSeed = tag.contains("ScheduleSeed", Tag.TAG_STRING)
                ? scheduleSeedFromTagName(tag.getString("ScheduleSeed"))
                : BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE;
        BannerModSettlementResidentScheduleWindowSeed scheduleWindowSeed = tag.contains("ScheduleWindowSeed", Tag.TAG_STRING)
                ? BannerModSettlementResidentScheduleWindowSeed.fromTagName(tag.getString("ScheduleWindowSeed"))
                : BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX;
        String goalDomainId = tag.contains("GoalDomainId", Tag.TAG_STRING)
                ? tag.getString("GoalDomainId")
                : "village";
        return new BannerModSettlementResidentSchedulePolicy(
                policySeed,
                scheduleSeed,
                scheduleWindowSeed,
                goalDomainId,
                tag.getBoolean("PrefersLocalBuilding")
        );
    }

    public static BannerModSettlementResidentSchedulePolicy defaultFor(BannerModSettlementResidentScheduleSeed scheduleSeed,
                                                                       BannerModSettlementResidentScheduleWindowSeed scheduleWindowSeed,
                                                                       BannerModSettlementResidentRuntimeRoleState runtimeRoleState,
                                                                       BannerModSettlementResidentRoleProfile roleProfile) {
        return new BannerModSettlementResidentSchedulePolicy(
                defaultPolicySeed(scheduleSeed, scheduleWindowSeed, runtimeRoleState),
                scheduleSeed,
                scheduleWindowSeed,
                roleProfile.goalDomainId(),
                roleProfile.prefersLocalBuilding()
        );
    }

    private static BannerModSettlementResidentSchedulePolicySeed defaultPolicySeed(BannerModSettlementResidentScheduleSeed scheduleSeed,
                                                                                   BannerModSettlementResidentScheduleWindowSeed scheduleWindowSeed,
                                                                                   BannerModSettlementResidentRuntimeRoleState runtimeRoleState) {
        return switch (runtimeRoleState) {
            case GOVERNANCE -> BannerModSettlementResidentSchedulePolicySeed.GOVERNANCE_CIVIC;
            case LOCAL_LABOR -> BannerModSettlementResidentSchedulePolicySeed.LOCAL_LABOR_DAY;
            case FLOATING_LABOR -> scheduleWindowSeed == BannerModSettlementResidentScheduleWindowSeed.LABOR_DAY
                    || scheduleSeed == BannerModSettlementResidentScheduleSeed.ASSIGNED_WORK
                    ? BannerModSettlementResidentSchedulePolicySeed.LOCAL_LABOR_DAY
                    : BannerModSettlementResidentSchedulePolicySeed.FLOATING_LABOR_FLEX;
            case ORPHANED_LABOR_ASSIGNMENT -> BannerModSettlementResidentSchedulePolicySeed.ORPHANED_LABOR_DAY;
            case VILLAGE_LIFE -> scheduleWindowSeed == BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY
                    || scheduleSeed == BannerModSettlementResidentScheduleSeed.GOVERNING
                    ? BannerModSettlementResidentSchedulePolicySeed.GOVERNANCE_CIVIC
                    : BannerModSettlementResidentSchedulePolicySeed.VILLAGE_LIFE_FLEX;
        };
    }

    private static BannerModSettlementResidentScheduleSeed scheduleSeedFromTagName(String name) {
        try {
            return BannerModSettlementResidentScheduleSeed.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE;
        }
    }
}
