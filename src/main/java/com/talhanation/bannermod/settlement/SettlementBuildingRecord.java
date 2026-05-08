package com.talhanation.bannermod.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SettlementBuildingRecord(
        UUID buildingUuid,
        String buildingTypeId,
        BlockPos originPos,
        @Nullable UUID ownerUuid,
        @Nullable String teamId,
        int residentCapacity,
        int workplaceSlots,
        int assignedWorkerCount,
        List<UUID> assignedResidentUuids,
        boolean stockpileBuilding,
        int stockpileContainerCount,
        int stockpileSlotCapacity,
        boolean stockpileRouteAuthored,
        boolean stockpilePortEntrypoint,
        List<String> stockpileTypeIds,
        SettlementBuildingCategory buildingCategory,
        SettlementBuildingProfileSeed buildingProfileSeed
) {
    public SettlementBuildingRecord {
        residentCapacity = Math.max(0, residentCapacity);
        workplaceSlots = Math.max(0, workplaceSlots);
        assignedWorkerCount = Math.max(0, assignedWorkerCount);
        stockpileContainerCount = Math.max(0, stockpileContainerCount);
        stockpileSlotCapacity = Math.max(0, stockpileSlotCapacity);
        assignedResidentUuids = List.copyOf(assignedResidentUuids == null ? List.of() : assignedResidentUuids);
        stockpileTypeIds = List.copyOf(stockpileTypeIds == null ? List.of() : stockpileTypeIds);
        buildingProfileSeed = buildingProfileSeed == null ? SettlementBuildingProfileSeed.fromBuildingTypeId(buildingTypeId) : buildingProfileSeed;
        buildingCategory = buildingCategory == null ? buildingProfileSeed.category() : buildingCategory;
    }

    public SettlementBuildingRecord(UUID buildingUuid,
                                             String buildingTypeId,
                                             BlockPos originPos,
                                             @Nullable UUID ownerUuid,
                                             @Nullable String teamId,
                                             int residentCapacity,
                                             int workplaceSlots,
                                             int assignedWorkerCount,
                                             List<UUID> assignedResidentUuids) {
        this(
                buildingUuid,
                buildingTypeId,
                originPos,
                ownerUuid,
                teamId,
                residentCapacity,
                workplaceSlots,
                assignedWorkerCount,
                assignedResidentUuids,
                false,
                0,
                0,
                false,
                false,
                List.of(),
                SettlementBuildingProfileSeed.fromBuildingTypeId(buildingTypeId).category(),
                SettlementBuildingProfileSeed.fromBuildingTypeId(buildingTypeId)
        );
    }

    public SettlementBuildingRecord(UUID buildingUuid,
                                             String buildingTypeId,
                                             BlockPos originPos,
                                             @Nullable UUID ownerUuid,
                                             @Nullable String teamId,
                                             int residentCapacity,
                                             int workplaceSlots,
                                             int assignedWorkerCount,
                                             List<UUID> assignedResidentUuids,
                                             boolean stockpileBuilding,
                                             int stockpileContainerCount,
                                             int stockpileSlotCapacity,
                                             boolean stockpileRouteAuthored,
                                             boolean stockpilePortEntrypoint,
                                             List<String> stockpileTypeIds) {
        this(
                buildingUuid,
                buildingTypeId,
                originPos,
                ownerUuid,
                teamId,
                residentCapacity,
                workplaceSlots,
                assignedWorkerCount,
                assignedResidentUuids,
                stockpileBuilding,
                stockpileContainerCount,
                stockpileSlotCapacity,
                stockpileRouteAuthored,
                stockpilePortEntrypoint,
                stockpileTypeIds,
                SettlementBuildingProfileSeed.fromBuildingTypeId(buildingTypeId).category(),
                SettlementBuildingProfileSeed.fromBuildingTypeId(buildingTypeId)
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("BuildingUuid", this.buildingUuid);
        tag.putString("BuildingTypeId", this.buildingTypeId);
        tag.putLong("OriginPos", this.originPos.asLong());
        if (this.ownerUuid != null) {
            tag.putUUID("OwnerUuid", this.ownerUuid);
        }
        if (this.teamId != null && !this.teamId.isBlank()) {
            tag.putString("TeamId", this.teamId);
        }
        tag.putInt("ResidentCapacity", this.residentCapacity);
        tag.putInt("WorkplaceSlots", this.workplaceSlots);
        tag.putInt("AssignedWorkerCount", this.assignedWorkerCount);
        ListTag assignedResidents = new ListTag();
        for (UUID residentUuid : this.assignedResidentUuids) {
            CompoundTag residentTag = new CompoundTag();
            residentTag.putUUID("ResidentUuid", residentUuid);
            assignedResidents.add(residentTag);
        }
        tag.put("AssignedResidentUuids", assignedResidents);
        tag.putBoolean("StockpileBuilding", this.stockpileBuilding);
        tag.putInt("StockpileContainerCount", this.stockpileContainerCount);
        tag.putInt("StockpileSlotCapacity", this.stockpileSlotCapacity);
        tag.putBoolean("StockpileRouteAuthored", this.stockpileRouteAuthored);
        tag.putBoolean("StockpilePortEntrypoint", this.stockpilePortEntrypoint);
        tag.putString("BuildingCategory", this.buildingCategory.name());
        tag.putString("BuildingProfileSeed", this.buildingProfileSeed.name());
        ListTag stockpileTypes = new ListTag();
        for (String stockpileTypeId : this.stockpileTypeIds) {
            if (stockpileTypeId != null && !stockpileTypeId.isBlank()) {
                stockpileTypes.add(StringTag.valueOf(stockpileTypeId));
            }
        }
        tag.put("StockpileTypeIds", stockpileTypes);
        return tag;
    }

    public static SettlementBuildingRecord fromTag(CompoundTag tag) {
        UUID ownerUuid = tag.hasUUID("OwnerUuid") ? tag.getUUID("OwnerUuid") : null;
        String teamId = tag.contains("TeamId", Tag.TAG_STRING) ? tag.getString("TeamId") : null;
        String buildingTypeId = tag.getString("BuildingTypeId");
        SettlementBuildingProfileSeed profileSeed = tag.contains("BuildingProfileSeed", Tag.TAG_STRING)
                ? SettlementBuildingProfileSeed.fromTagName(tag.getString("BuildingProfileSeed"))
                : SettlementBuildingProfileSeed.fromBuildingTypeId(buildingTypeId);
        return new SettlementBuildingRecord(
                tag.getUUID("BuildingUuid"),
                buildingTypeId,
                BlockPos.of(tag.getLong("OriginPos")),
                ownerUuid,
                teamId,
                tag.getInt("ResidentCapacity"),
                tag.getInt("WorkplaceSlots"),
                tag.getInt("AssignedWorkerCount"),
                readAssignedResidentUuids(tag.getList("AssignedResidentUuids", Tag.TAG_COMPOUND)),
                tag.getBoolean("StockpileBuilding"),
                tag.getInt("StockpileContainerCount"),
                tag.getInt("StockpileSlotCapacity"),
                tag.getBoolean("StockpileRouteAuthored"),
                tag.getBoolean("StockpilePortEntrypoint"),
                readStockpileTypeIds(tag.getList("StockpileTypeIds", Tag.TAG_STRING)),
                tag.contains("BuildingCategory", Tag.TAG_STRING)
                        ? SettlementBuildingCategory.fromTagName(tag.getString("BuildingCategory"))
                        : profileSeed.category(),
                profileSeed
        );
    }

    private static List<UUID> readAssignedResidentUuids(ListTag list) {
        List<UUID> assignedResidentUuids = new ArrayList<>();
        for (Tag entry : list) {
            CompoundTag residentTag = (CompoundTag) entry;
            if (residentTag.hasUUID("ResidentUuid")) {
                assignedResidentUuids.add(residentTag.getUUID("ResidentUuid"));
            }
        }
        return assignedResidentUuids;
    }

    private static List<String> readStockpileTypeIds(ListTag list) {
        List<String> stockpileTypeIds = new ArrayList<>();
        for (Tag entry : list) {
            stockpileTypeIds.add(entry.getAsString());
        }
        return stockpileTypeIds;
    }
}
