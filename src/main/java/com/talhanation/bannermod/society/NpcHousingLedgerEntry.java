package com.talhanation.bannermod.society;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public record NpcHousingLedgerEntry(
        UUID householdId,
        UUID residentUuid,
        UUID claimUuid,
        @Nullable UUID headResidentUuid,
        @Nullable UUID homeBuildingUuid,
        @Nullable UUID buildAreaUuid,
        @Nullable BlockPos reservedPlotPos,
        String statusTag,
        String housingStateTag,
        String urgencyTag,
        String reasonTag,
        int householdSize,
        int waitingDays,
        int priorityScore,
        int queueRank,
        long requestedAtGameTime,
        long updatedAtGameTime
) {
    public NpcHousingLedgerEntry {
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        if (residentUuid == null) {
            throw new IllegalArgumentException("residentUuid must not be null");
        }
        if (claimUuid == null) {
            throw new IllegalArgumentException("claimUuid must not be null");
        }
        statusTag = safeTag(statusTag);
        housingStateTag = safeTag(housingStateTag);
        urgencyTag = safeTag(urgencyTag);
        reasonTag = safeTag(reasonTag);
        householdSize = Math.max(0, householdSize);
        waitingDays = Math.max(0, waitingDays);
        priorityScore = Math.max(0, priorityScore);
        queueRank = Math.max(0, queueRank);
    }

    public NpcHousingLedgerEntry withQueueRank(int queueRank) {
        return new NpcHousingLedgerEntry(
                this.householdId,
                this.residentUuid,
                this.claimUuid,
                this.headResidentUuid,
                this.homeBuildingUuid,
                this.buildAreaUuid,
                this.reservedPlotPos,
                this.statusTag,
                this.housingStateTag,
                this.urgencyTag,
                this.reasonTag,
                this.householdSize,
                this.waitingDays,
                this.priorityScore,
                queueRank,
                this.requestedAtGameTime,
                this.updatedAtGameTime
        );
    }

    public String statusTranslationKey() {
        return "gui.bannermod.society.housing_request." + safeTag(this.statusTag).toLowerCase(Locale.ROOT);
    }

    public String housingStateTranslationKey() {
        return "gui.bannermod.society.household_housing." + safeTag(this.housingStateTag).toLowerCase(Locale.ROOT);
    }

    public String urgencyTranslationKey() {
        return "gui.bannermod.housing_ledger.urgency." + safeTag(this.urgencyTag).toLowerCase(Locale.ROOT);
    }

    public String reasonTranslationKey() {
        return "gui.bannermod.housing_ledger.reason." + safeTag(this.reasonTag).toLowerCase(Locale.ROOT);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("HouseholdId", this.householdId);
        tag.putUUID("ResidentUuid", this.residentUuid);
        tag.putUUID("ClaimUuid", this.claimUuid);
        if (this.headResidentUuid != null) {
            tag.putUUID("HeadResidentUuid", this.headResidentUuid);
        }
        if (this.homeBuildingUuid != null) {
            tag.putUUID("HomeBuildingUuid", this.homeBuildingUuid);
        }
        if (this.buildAreaUuid != null) {
            tag.putUUID("BuildAreaUuid", this.buildAreaUuid);
        }
        if (this.reservedPlotPos != null) {
            tag.putLong("ReservedPlotPos", this.reservedPlotPos.asLong());
        }
        tag.putString("Status", this.statusTag);
        tag.putString("HousingState", this.housingStateTag);
        tag.putString("Urgency", this.urgencyTag);
        tag.putString("Reason", this.reasonTag);
        tag.putInt("HouseholdSize", this.householdSize);
        tag.putInt("WaitingDays", this.waitingDays);
        tag.putInt("PriorityScore", this.priorityScore);
        tag.putInt("QueueRank", this.queueRank);
        tag.putLong("RequestedAt", this.requestedAtGameTime);
        tag.putLong("UpdatedAt", this.updatedAtGameTime);
        return tag;
    }

    public static NpcHousingLedgerEntry fromTag(CompoundTag tag) {
        return new NpcHousingLedgerEntry(
                tag.getUUID("HouseholdId"),
                tag.getUUID("ResidentUuid"),
                tag.getUUID("ClaimUuid"),
                tag.contains("HeadResidentUuid") ? tag.getUUID("HeadResidentUuid") : null,
                tag.contains("HomeBuildingUuid") ? tag.getUUID("HomeBuildingUuid") : null,
                tag.contains("BuildAreaUuid") ? tag.getUUID("BuildAreaUuid") : null,
                tag.contains("ReservedPlotPos") ? BlockPos.of(tag.getLong("ReservedPlotPos")) : null,
                tag.getString("Status"),
                tag.getString("HousingState"),
                tag.getString("Urgency"),
                tag.getString("Reason"),
                tag.getInt("HouseholdSize"),
                tag.getInt("WaitingDays"),
                tag.getInt("PriorityScore"),
                tag.getInt("QueueRank"),
                tag.getLong("RequestedAt"),
                tag.getLong("UpdatedAt")
        );
    }

    private static String safeTag(@Nullable String tag) {
        return tag == null || tag.isBlank() ? "UNSPECIFIED" : tag;
    }
}
