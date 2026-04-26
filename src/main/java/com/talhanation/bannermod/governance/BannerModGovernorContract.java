package com.talhanation.bannermod.governance;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record BannerModGovernorContract(
        UUID contractId,
        UUID claimUuid,
        BannerModGovernorContractType type,
        int calculatedReward,
        int maxReward,
        @Nullable UUID acceptedByUuid,
        long postedTick,
        long deadlineTick,
        boolean pinned,
        BannerModGovernorContractStatus status
) {
    public boolean isOpen() {
        return this.status == BannerModGovernorContractStatus.OPEN;
    }

    public boolean isExpired(long currentTick) {
        return !pinned && currentTick > deadlineTick && status == BannerModGovernorContractStatus.OPEN;
    }

    public BannerModGovernorContract withStatus(BannerModGovernorContractStatus newStatus) {
        return new BannerModGovernorContract(contractId, claimUuid, type, calculatedReward, maxReward,
                acceptedByUuid, postedTick, deadlineTick, pinned, newStatus);
    }

    public BannerModGovernorContract withAcceptedBy(UUID playerUuid) {
        return new BannerModGovernorContract(contractId, claimUuid, type, calculatedReward, maxReward,
                playerUuid, postedTick, deadlineTick, pinned, BannerModGovernorContractStatus.ACCEPTED);
    }

    public BannerModGovernorContract withPinned(boolean newPinned) {
        return new BannerModGovernorContract(contractId, claimUuid, type, calculatedReward, maxReward,
                acceptedByUuid, postedTick, deadlineTick, newPinned, status);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ContractId", contractId);
        tag.putUUID("ClaimUuid", claimUuid);
        tag.putString("Type", type.token());
        tag.putInt("CalculatedReward", calculatedReward);
        tag.putInt("MaxReward", maxReward);
        if (acceptedByUuid != null) tag.putUUID("AcceptedByUuid", acceptedByUuid);
        tag.putLong("PostedTick", postedTick);
        tag.putLong("DeadlineTick", deadlineTick);
        tag.putBoolean("Pinned", pinned);
        tag.putString("Status", status.name());
        return tag;
    }

    public static BannerModGovernorContract fromTag(CompoundTag tag) {
        return new BannerModGovernorContract(
                tag.getUUID("ContractId"),
                tag.getUUID("ClaimUuid"),
                BannerModGovernorContractType.fromToken(tag.getString("Type")),
                tag.getInt("CalculatedReward"),
                tag.getInt("MaxReward"),
                tag.hasUUID("AcceptedByUuid") ? tag.getUUID("AcceptedByUuid") : null,
                tag.getLong("PostedTick"),
                tag.getLong("DeadlineTick"),
                tag.contains("Pinned") && tag.getBoolean("Pinned"),
                BannerModGovernorContractStatus.fromName(tag.getString("Status"))
        );
    }
}
