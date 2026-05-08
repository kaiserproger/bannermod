package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;

public record SettlementSellerDispatchRecord(
        UUID residentUuid,
        UUID marketUuid,
        @Nullable String marketName,
        SettlementSellerDispatchState dispatchState
) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ResidentUuid", this.residentUuid);
        tag.putUUID("MarketUuid", this.marketUuid);
        if (this.marketName != null && !this.marketName.isBlank()) {
            tag.putString("MarketName", this.marketName);
        }
        tag.putString("DispatchState", this.dispatchState.name());
        return tag;
    }

    public static SettlementSellerDispatchRecord fromTag(CompoundTag tag) {
        return new SettlementSellerDispatchRecord(
                tag.getUUID("ResidentUuid"),
                tag.getUUID("MarketUuid"),
                tag.contains("MarketName", Tag.TAG_STRING) ? tag.getString("MarketName") : null,
                tag.contains("DispatchState", Tag.TAG_STRING)
                        ? dispatchStateFromTagName(tag.getString("DispatchState"))
                        : SettlementSellerDispatchState.READY
        );
    }

    private static SettlementSellerDispatchState dispatchStateFromTagName(String name) {
        try {
            return SettlementSellerDispatchState.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return SettlementSellerDispatchState.READY;
        }
    }
}
