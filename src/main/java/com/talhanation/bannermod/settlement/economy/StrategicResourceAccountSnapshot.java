package com.talhanation.bannermod.settlement.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

public record StrategicResourceAccountSnapshot(
        UUID claimUuid,
        int food,
        int iron,
        int wood,
        int stone,
        String lastBucketId,
        int lastCreditAmount,
        long lastCreditTick,
        int lastDebitAmount,
        long lastDebitTick
) {
    public StrategicResourceAccountSnapshot {
        food = Math.max(0, food);
        iron = Math.max(0, iron);
        wood = Math.max(0, wood);
        stone = Math.max(0, stone);
        lastBucketId = lastBucketId == null ? "" : lastBucketId;
        lastCreditAmount = Math.max(0, lastCreditAmount);
        lastDebitAmount = Math.max(0, lastDebitAmount);
    }

    public int balance(StrategicResourceBucket bucket) {
        return switch (bucket) {
            case FOOD -> this.food;
            case IRON -> this.iron;
            case WOOD -> this.wood;
            case STONE -> this.stone;
            case COINS -> 0;
        };
    }

    public StrategicResourceAccountSnapshot withCredit(StrategicResourceBucket bucket, int amount, long tick) {
        int normalizedAmount = Math.max(0, amount);
        return withBalance(bucket, balance(bucket) + normalizedAmount, normalizedAmount, tick, this.lastDebitAmount, this.lastDebitTick);
    }

    public StrategicResourceAccountSnapshot withDebit(StrategicResourceBucket bucket, int amount, long tick) {
        int normalizedAmount = Math.max(0, amount);
        return withBalance(bucket, balance(bucket) - normalizedAmount, this.lastCreditAmount, this.lastCreditTick, normalizedAmount, tick);
    }

    private StrategicResourceAccountSnapshot withBalance(StrategicResourceBucket bucket,
                                                        int balance,
                                                        int creditAmount,
                                                        long creditTick,
                                                        int debitAmount,
                                                        long debitTick) {
        int updatedFood = bucket == StrategicResourceBucket.FOOD ? balance : this.food;
        int updatedIron = bucket == StrategicResourceBucket.IRON ? balance : this.iron;
        int updatedWood = bucket == StrategicResourceBucket.WOOD ? balance : this.wood;
        int updatedStone = bucket == StrategicResourceBucket.STONE ? balance : this.stone;
        return new StrategicResourceAccountSnapshot(
                this.claimUuid,
                updatedFood,
                updatedIron,
                updatedWood,
                updatedStone,
                bucket.id(),
                creditAmount,
                creditAmount > 0 ? creditTick : this.lastCreditTick,
                debitAmount,
                debitAmount > 0 ? debitTick : this.lastDebitTick
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ClaimUuid", this.claimUuid);
        tag.putInt("Food", this.food);
        tag.putInt("Iron", this.iron);
        tag.putInt("Wood", this.wood);
        tag.putInt("Stone", this.stone);
        if (!this.lastBucketId.isBlank()) {
            tag.putString("LastBucketId", this.lastBucketId);
        }
        tag.putInt("LastCreditAmount", this.lastCreditAmount);
        tag.putLong("LastCreditTick", this.lastCreditTick);
        tag.putInt("LastDebitAmount", this.lastDebitAmount);
        tag.putLong("LastDebitTick", this.lastDebitTick);
        return tag;
    }

    public static StrategicResourceAccountSnapshot empty(UUID claimUuid) {
        return new StrategicResourceAccountSnapshot(claimUuid, 0, 0, 0, 0, "", 0, 0L, 0, 0L);
    }

    public static StrategicResourceAccountSnapshot fromTag(CompoundTag tag) {
        return new StrategicResourceAccountSnapshot(
                tag.getUUID("ClaimUuid"),
                tag.getInt("Food"),
                tag.getInt("Iron"),
                tag.getInt("Wood"),
                tag.getInt("Stone"),
                tag.contains("LastBucketId", Tag.TAG_STRING) ? tag.getString("LastBucketId") : "",
                tag.getInt("LastCreditAmount"),
                tag.getLong("LastCreditTick"),
                tag.getInt("LastDebitAmount"),
                tag.getLong("LastDebitTick")
        );
    }
}
