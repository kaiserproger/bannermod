package com.talhanation.bannermod.war.runtime;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record TreatyDefaultFact(
        UUID id,
        UUID treatyId,
        UUID payerEntityId,
        UUID receiverEntityId,
        String defaultType,
        int requestedAmount,
        int paidAmount,
        int defaultedAmount,
        long gameTime
) {
    public TreatyDefaultFact {
        defaultType = defaultType == null ? "" : defaultType;
        requestedAmount = Math.max(0, requestedAmount);
        paidAmount = Math.max(0, paidAmount);
        defaultedAmount = Math.max(0, defaultedAmount);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("TreatyId", treatyId);
        tag.putUUID("PayerEntityId", payerEntityId);
        tag.putUUID("ReceiverEntityId", receiverEntityId);
        tag.putString("DefaultType", defaultType);
        tag.putInt("RequestedAmount", requestedAmount);
        tag.putInt("PaidAmount", paidAmount);
        tag.putInt("DefaultedAmount", defaultedAmount);
        tag.putLong("GameTime", gameTime);
        return tag;
    }

    public static TreatyDefaultFact fromTag(CompoundTag tag) {
        return new TreatyDefaultFact(
                tag.getUUID("Id"),
                tag.getUUID("TreatyId"),
                tag.getUUID("PayerEntityId"),
                tag.getUUID("ReceiverEntityId"),
                tag.getString("DefaultType"),
                tag.getInt("RequestedAmount"),
                tag.getInt("PaidAmount"),
                tag.getInt("DefaultedAmount"),
                tag.getLong("GameTime")
        );
    }
}
