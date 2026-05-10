package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.settlement.economy.StrategicResourceBucket;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public record TributeTreatyRecord(
        UUID id,
        UUID payerEntityId,
        UUID receiverEntityId,
        StrategicResourceBucket resourceBucket,
        int amount,
        long intervalTicks,
        UUID sourceWarId,
        @Nullable UUID sourceClaimUuid,
        long createdAtGameTime,
        long lastPaidAtGameTime,
        int missedPayments,
        int defaultedAmount,
        boolean active
) {
    public TributeTreatyRecord {
        resourceBucket = resourceBucket == null ? StrategicResourceBucket.COINS : resourceBucket;
        amount = Math.max(0, amount);
        intervalTicks = Math.max(0L, intervalTicks);
        missedPayments = Math.max(0, missedPayments);
        defaultedAmount = Math.max(0, defaultedAmount);
    }

    public TributeTreatyRecord withPayment(long paidAtGameTime) {
        return new TributeTreatyRecord(id, payerEntityId, receiverEntityId, resourceBucket, amount,
                intervalTicks, sourceWarId, sourceClaimUuid, createdAtGameTime, paidAtGameTime,
                missedPayments, defaultedAmount, active);
    }

    public TributeTreatyRecord withDefault(int defaulted) {
        return new TributeTreatyRecord(id, payerEntityId, receiverEntityId, resourceBucket, amount,
                intervalTicks, sourceWarId, sourceClaimUuid, createdAtGameTime, lastPaidAtGameTime,
                missedPayments + 1, defaultedAmount + Math.max(0, defaulted), active);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("PayerEntityId", payerEntityId);
        tag.putUUID("ReceiverEntityId", receiverEntityId);
        tag.putString("ResourceBucket", resourceBucket.id());
        tag.putInt("Amount", amount);
        tag.putLong("IntervalTicks", intervalTicks);
        if (sourceWarId != null) {
            tag.putUUID("SourceWarId", sourceWarId);
        }
        if (sourceClaimUuid != null) {
            tag.putUUID("SourceClaimUuid", sourceClaimUuid);
        }
        tag.putLong("CreatedAtGameTime", createdAtGameTime);
        tag.putLong("LastPaidAtGameTime", lastPaidAtGameTime);
        tag.putInt("MissedPayments", missedPayments);
        tag.putInt("DefaultedAmount", defaultedAmount);
        tag.putBoolean("Active", active);
        return tag;
    }

    public static TributeTreatyRecord fromTag(CompoundTag tag) {
        return new TributeTreatyRecord(
                tag.getUUID("Id"),
                tag.getUUID("PayerEntityId"),
                tag.getUUID("ReceiverEntityId"),
                bucketFromId(tag.getString("ResourceBucket")),
                tag.getInt("Amount"),
                tag.getLong("IntervalTicks"),
                tag.hasUUID("SourceWarId") ? tag.getUUID("SourceWarId") : null,
                tag.hasUUID("SourceClaimUuid") ? tag.getUUID("SourceClaimUuid") : null,
                tag.getLong("CreatedAtGameTime"),
                tag.getLong("LastPaidAtGameTime"),
                tag.getInt("MissedPayments"),
                tag.getInt("DefaultedAmount"),
                !tag.contains("Active") || tag.getBoolean("Active")
        );
    }

    private static StrategicResourceBucket bucketFromId(String id) {
        if (id != null) {
            for (StrategicResourceBucket bucket : StrategicResourceBucket.values()) {
                if (bucket.id().equals(id.toLowerCase(Locale.ROOT))) {
                    return bucket;
                }
            }
        }
        return StrategicResourceBucket.COINS;
    }
}
