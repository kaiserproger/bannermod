package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record SettlementStockpileSummary(
        int storageBuildingCount,
        int containerCount,
        int slotCapacity,
        int routedStorageCount,
        int portEntrypointCount,
        List<String> authoredStorageTypeIds
) {
    public SettlementStockpileSummary {
        storageBuildingCount = Math.max(0, storageBuildingCount);
        containerCount = Math.max(0, containerCount);
        slotCapacity = Math.max(0, slotCapacity);
        routedStorageCount = Math.max(0, routedStorageCount);
        portEntrypointCount = Math.max(0, portEntrypointCount);
        authoredStorageTypeIds = List.copyOf(authoredStorageTypeIds == null ? List.of() : authoredStorageTypeIds);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("StorageBuildingCount", this.storageBuildingCount);
        tag.putInt("ContainerCount", this.containerCount);
        tag.putInt("SlotCapacity", this.slotCapacity);
        tag.putInt("RoutedStorageCount", this.routedStorageCount);
        tag.putInt("PortEntrypointCount", this.portEntrypointCount);
        ListTag storageTypes = new ListTag();
        for (String storageTypeId : this.authoredStorageTypeIds) {
            if (storageTypeId != null && !storageTypeId.isBlank()) {
                storageTypes.add(StringTag.valueOf(storageTypeId));
            }
        }
        tag.put("AuthoredStorageTypeIds", storageTypes);
        return tag;
    }

    public static SettlementStockpileSummary fromTag(CompoundTag tag) {
        return new SettlementStockpileSummary(
                tag.getInt("StorageBuildingCount"),
                tag.getInt("ContainerCount"),
                tag.getInt("SlotCapacity"),
                tag.getInt("RoutedStorageCount"),
                tag.getInt("PortEntrypointCount"),
                readStorageTypeIds(tag.getList("AuthoredStorageTypeIds", Tag.TAG_STRING))
        );
    }

    public static SettlementStockpileSummary empty() {
        return new SettlementStockpileSummary(0, 0, 0, 0, 0, List.of());
    }

    private static List<String> readStorageTypeIds(ListTag list) {
        List<String> storageTypeIds = new ArrayList<>();
        for (Tag entry : list) {
            storageTypeIds.add(entry.getAsString());
        }
        return storageTypeIds;
    }
}
