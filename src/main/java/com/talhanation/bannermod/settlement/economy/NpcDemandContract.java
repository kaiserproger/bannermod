package com.talhanation.bannermod.settlement.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record NpcDemandContract(
        UUID contractUuid,
        UUID claimUuid,
        String buyer,
        String resourceBucket,
        List<String> requestedItems,
        int amount,
        long createdAtGameTime,
        long deadlineGameTime,
        int rewardCoins,
        Status status
) {
    public NpcDemandContract {
        contractUuid = contractUuid == null ? UUID.randomUUID() : contractUuid;
        claimUuid = claimUuid == null ? new UUID(0L, 0L) : claimUuid;
        buyer = buyer == null || buyer.isBlank() ? "wandering_buyer" : buyer;
        resourceBucket = resourceBucket == null || resourceBucket.isBlank() ? "supplies" : resourceBucket;
        requestedItems = List.copyOf(requestedItems == null ? List.of() : requestedItems);
        amount = Math.max(1, amount);
        deadlineGameTime = Math.max(createdAtGameTime + 1, deadlineGameTime);
        rewardCoins = Math.max(1, rewardCoins);
        status = status == null ? Status.OPEN : status;
    }

    public NpcDemandContract expired(long gameTime) {
        if (this.status != Status.OPEN || gameTime < this.deadlineGameTime) {
            return this;
        }
        return new NpcDemandContract(
                this.contractUuid,
                this.claimUuid,
                this.buyer,
                this.resourceBucket,
                this.requestedItems,
                this.amount,
                this.createdAtGameTime,
                this.deadlineGameTime,
                this.rewardCoins,
                Status.EXPIRED
        );
    }

    public NpcDemandContract fulfilled() {
        if (this.status == Status.FULFILLED) {
            return this;
        }
        return new NpcDemandContract(
                this.contractUuid,
                this.claimUuid,
                this.buyer,
                this.resourceBucket,
                this.requestedItems,
                this.amount,
                this.createdAtGameTime,
                this.deadlineGameTime,
                this.rewardCoins,
                Status.FULFILLED
        );
    }

    public String debugLine() {
        return "contract=" + this.contractUuid
                + " claim=" + this.claimUuid
                + " buyer=" + this.buyer
                + " bucket=" + this.resourceBucket
                + " items=" + String.join(",", this.requestedItems)
                + " amount=" + this.amount
                + " deadline=" + this.deadlineGameTime
                + " reward=" + this.rewardCoins
                + " status=" + this.status;
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ContractUuid", this.contractUuid);
        tag.putUUID("ClaimUuid", this.claimUuid);
        tag.putString("Buyer", this.buyer);
        tag.putString("ResourceBucket", this.resourceBucket);
        ListTag items = new ListTag();
        for (String item : this.requestedItems) {
            items.add(StringTag.valueOf(item));
        }
        tag.put("RequestedItems", items);
        tag.putInt("Amount", this.amount);
        tag.putLong("CreatedAtGameTime", this.createdAtGameTime);
        tag.putLong("DeadlineGameTime", this.deadlineGameTime);
        tag.putInt("RewardCoins", this.rewardCoins);
        tag.putString("Status", this.status.name());
        return tag;
    }

    static NpcDemandContract fromTag(CompoundTag tag) {
        List<String> items = new ArrayList<>();
        if (tag.contains("RequestedItems", Tag.TAG_LIST)) {
            ListTag itemTags = tag.getList("RequestedItems", Tag.TAG_STRING);
            for (Tag itemTag : itemTags) {
                items.add(itemTag.getAsString());
            }
        }
        return new NpcDemandContract(
                tag.hasUUID("ContractUuid") ? tag.getUUID("ContractUuid") : UUID.randomUUID(),
                tag.hasUUID("ClaimUuid") ? tag.getUUID("ClaimUuid") : new UUID(0L, 0L),
                tag.getString("Buyer"),
                tag.getString("ResourceBucket"),
                items,
                tag.getInt("Amount"),
                tag.getLong("CreatedAtGameTime"),
                tag.getLong("DeadlineGameTime"),
                tag.getInt("RewardCoins"),
                statusFromTag(tag.getString("Status"))
        );
    }

    private static Status statusFromTag(String statusName) {
        try {
            return Status.valueOf(statusName);
        } catch (RuntimeException exception) {
            return Status.OPEN;
        }
    }

    public enum Status {
        OPEN,
        FULFILLED,
        EXPIRED
    }
}
