package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public record SettlementProjectCandidateSnapshot(
        String candidateId,
        @Nullable SettlementBuildingProfileSeed targetBuildingProfileSeed,
        int priority,
        boolean governedSettlement,
        boolean claimedSettlement,
        List<String> driverIds
) {
    public SettlementProjectCandidateSnapshot {
        candidateId = candidateId == null || candidateId.isBlank() ? "none" : candidateId;
        priority = Math.max(0, priority);
        driverIds = List.copyOf(driverIds == null ? List.of() : driverIds);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CandidateId", this.candidateId);
        if (this.targetBuildingProfileSeed != null) {
            tag.putString("TargetBuildingProfileSeed", this.targetBuildingProfileSeed.name());
        }
        tag.putInt("Priority", this.priority);
        tag.putBoolean("GovernedSettlement", this.governedSettlement);
        tag.putBoolean("ClaimedSettlement", this.claimedSettlement);
        ListTag driverList = new ListTag();
        for (String driverId : this.driverIds) {
            if (driverId != null && !driverId.isBlank()) {
                driverList.add(StringTag.valueOf(driverId));
            }
        }
        tag.put("DriverIds", driverList);
        return tag;
    }

    public static SettlementProjectCandidateSnapshot fromTag(CompoundTag tag) {
        SettlementBuildingProfileSeed targetBuildingProfileSeed = tag.contains("TargetBuildingProfileSeed", Tag.TAG_STRING)
                ? SettlementBuildingProfileSeed.fromTagName(tag.getString("TargetBuildingProfileSeed"))
                : null;
        return new SettlementProjectCandidateSnapshot(
                tag.contains("CandidateId", Tag.TAG_STRING) ? tag.getString("CandidateId") : "none",
                targetBuildingProfileSeed,
                tag.getInt("Priority"),
                tag.getBoolean("GovernedSettlement"),
                tag.getBoolean("ClaimedSettlement"),
                readDriverIds(tag.getList("DriverIds", Tag.TAG_STRING))
        );
    }

    public static SettlementProjectCandidateSnapshot empty() {
        return new SettlementProjectCandidateSnapshot("none", null, 0, false, false, List.of());
    }

    private static List<String> readDriverIds(ListTag list) {
        List<String> driverIds = new ArrayList<>();
        for (Tag entry : list) {
            driverIds.add(entry.getAsString());
        }
        return driverIds;
    }
}
