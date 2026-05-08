package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record SettlementDesiredGoodSnapshot(
        String desiredGoodId,
        int driverCount
) {
    public SettlementDesiredGoodSnapshot {
        desiredGoodId = desiredGoodId == null ? "" : desiredGoodId;
        driverCount = Math.max(0, driverCount);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (!this.desiredGoodId.isBlank()) {
            tag.putString("DesiredGoodId", this.desiredGoodId);
        }
        tag.putInt("DriverCount", this.driverCount);
        return tag;
    }

    public static SettlementDesiredGoodSnapshot fromTag(CompoundTag tag) {
        return new SettlementDesiredGoodSnapshot(
                tag.contains("DesiredGoodId", Tag.TAG_STRING) ? tag.getString("DesiredGoodId") : "",
                tag.getInt("DriverCount")
        );
    }
}
