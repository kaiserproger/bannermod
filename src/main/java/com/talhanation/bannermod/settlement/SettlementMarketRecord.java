package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

public record SettlementMarketRecord(
        UUID buildingUuid,
        String marketName,
        boolean open,
        int totalStorageSlots,
        int freeStorageSlots
) {
    public SettlementMarketRecord {
        totalStorageSlots = Math.max(0, totalStorageSlots);
        freeStorageSlots = Math.max(0, Math.min(freeStorageSlots, totalStorageSlots));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("BuildingUuid", this.buildingUuid);
        if (this.marketName != null && !this.marketName.isBlank()) {
            tag.putString("MarketName", this.marketName);
        }
        tag.putBoolean("Open", this.open);
        tag.putInt("TotalStorageSlots", this.totalStorageSlots);
        tag.putInt("FreeStorageSlots", this.freeStorageSlots);
        return tag;
    }

    public static SettlementMarketRecord fromTag(CompoundTag tag) {
        String marketName = tag.contains("MarketName", Tag.TAG_STRING) ? tag.getString("MarketName") : "Market";
        return new SettlementMarketRecord(
                tag.getUUID("BuildingUuid"),
                marketName,
                tag.getBoolean("Open"),
                tag.getInt("TotalStorageSlots"),
                tag.getInt("FreeStorageSlots")
        );
    }
}
