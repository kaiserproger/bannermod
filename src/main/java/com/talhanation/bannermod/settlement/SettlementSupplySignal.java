package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record SettlementSupplySignal(
        String goodId,
        int desiredUnits,
        int coverageUnits,
        int shortageUnits,
        int reservationHintUnits
) {
    public SettlementSupplySignal {
        goodId = goodId == null ? "" : goodId;
        desiredUnits = Math.max(0, desiredUnits);
        coverageUnits = Math.max(0, coverageUnits);
        shortageUnits = Math.max(0, shortageUnits);
        reservationHintUnits = Math.max(0, reservationHintUnits);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (!this.goodId.isBlank()) {
            tag.putString("GoodId", this.goodId);
        }
        tag.putInt("DesiredUnits", this.desiredUnits);
        tag.putInt("CoverageUnits", this.coverageUnits);
        tag.putInt("ShortageUnits", this.shortageUnits);
        tag.putInt("ReservationHintUnits", this.reservationHintUnits);
        return tag;
    }

    public static SettlementSupplySignal fromTag(CompoundTag tag) {
        return new SettlementSupplySignal(
                tag.contains("GoodId", Tag.TAG_STRING) ? tag.getString("GoodId") : "",
                tag.getInt("DesiredUnits"),
                tag.getInt("CoverageUnits"),
                tag.getInt("ShortageUnits"),
                tag.getInt("ReservationHintUnits")
        );
    }
}
