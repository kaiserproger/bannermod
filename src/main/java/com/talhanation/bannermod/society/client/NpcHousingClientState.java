package com.talhanation.bannermod.society.client;

import com.talhanation.bannermod.society.NpcHousingLedgerEntry;
import com.talhanation.bannermod.society.NpcHousingSnapshotContract;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NpcHousingClientState {
    private static List<NpcHousingLedgerEntry> requests = List.of();
    private static Map<UUID, NpcHousingLedgerEntry> requestsByHousehold = Map.of();
    private static boolean hasClaim;
    private static boolean canManage;
    private static boolean hasSnapshot;
    private static boolean syncPending;
    private static String denialKey = "";
    @Nullable
    private static UUID claimUuid;
    private static int version;

    private NpcHousingClientState() {
    }

    public static List<NpcHousingLedgerEntry> requests() {
        return requests;
    }

    public static @Nullable NpcHousingLedgerEntry requestByHousehold(@Nullable UUID householdId) {
        return householdId == null ? null : requestsByHousehold.get(householdId);
    }

    public static boolean hasClaim() {
        return hasClaim;
    }

    public static boolean canManage() {
        return canManage;
    }

    public static boolean hasSnapshot() {
        return hasSnapshot;
    }

    public static boolean syncPending() {
        return syncPending;
    }

    public static String denialKey() {
        return denialKey;
    }

    public static @Nullable UUID claimUuid() {
        return claimUuid;
    }

    public static int version() {
        return version;
    }

    public static void beginSync() {
        syncPending = true;
    }

    public static void clear() {
        requests = List.of();
        requestsByHousehold = Map.of();
        hasClaim = false;
        canManage = false;
        hasSnapshot = false;
        syncPending = false;
        denialKey = "";
        claimUuid = null;
        version++;
    }

    public static void applyFromNbt(CompoundTag tag) {
        if (tag == null) {
            clear();
            return;
        }
        List<NpcHousingLedgerEntry> decoded = new ArrayList<>();
        for (Tag entry : tag.getList(NpcHousingSnapshotContract.NBT_REQUESTS, Tag.TAG_COMPOUND)) {
            decoded.add(NpcHousingLedgerEntry.fromTag((CompoundTag) entry));
        }
        requests = List.copyOf(decoded);
        Map<UUID, NpcHousingLedgerEntry> byHousehold = new HashMap<>();
        for (NpcHousingLedgerEntry entry : decoded) {
            byHousehold.put(entry.householdId(), entry);
        }
        requestsByHousehold = Map.copyOf(byHousehold);
        hasClaim = tag.getBoolean(NpcHousingSnapshotContract.NBT_HAS_CLAIM);
        claimUuid = hasClaim && tag.contains(NpcHousingSnapshotContract.NBT_CLAIM_UUID)
                ? tag.getUUID(NpcHousingSnapshotContract.NBT_CLAIM_UUID)
                : null;
        canManage = tag.getBoolean(NpcHousingSnapshotContract.NBT_CAN_MANAGE);
        denialKey = tag.contains(NpcHousingSnapshotContract.NBT_DENIAL_KEY)
                ? tag.getString(NpcHousingSnapshotContract.NBT_DENIAL_KEY)
                : "";
        hasSnapshot = true;
        syncPending = false;
        version++;
    }
}
