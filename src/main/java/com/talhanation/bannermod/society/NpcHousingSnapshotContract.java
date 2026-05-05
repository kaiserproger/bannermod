package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import javax.annotation.Nullable;
import java.util.UUID;

public final class NpcHousingSnapshotContract {
    public static final String NBT_HAS_CLAIM = "HasClaim";
    public static final String NBT_CLAIM_UUID = "ClaimUuid";
    public static final String NBT_CAN_MANAGE = "CanManage";
    public static final String NBT_DENIAL_KEY = "DenialKey";
    public static final String NBT_REQUESTS = "Requests";

    private NpcHousingSnapshotContract() {
    }

    public static CompoundTag encode(@Nullable UUID claimUuid,
                                     boolean canManage,
                                     @Nullable String denialKey,
                                     Iterable<NpcHousingLedgerEntry> requests) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(NBT_HAS_CLAIM, claimUuid != null);
        if (claimUuid != null) {
            tag.putUUID(NBT_CLAIM_UUID, claimUuid);
        }
        tag.putBoolean(NBT_CAN_MANAGE, canManage);
        if (denialKey != null && !denialKey.isBlank()) {
            tag.putString(NBT_DENIAL_KEY, denialKey);
        }
        ListTag requestTags = new ListTag();
        if (requests != null) {
            for (NpcHousingLedgerEntry entry : requests) {
                if (entry != null) {
                    requestTags.add(entry.toTag());
                }
            }
        }
        tag.put(NBT_REQUESTS, requestTags);
        return tag;
    }
}
