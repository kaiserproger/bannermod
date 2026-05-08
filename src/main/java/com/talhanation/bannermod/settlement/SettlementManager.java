package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.persistence.SavedDataVersioning;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SettlementManager extends SavedData {
    private static final String FILE_ID = "bannermodSettlements";
    private static final SavedData.Factory<SettlementManager> FACTORY = new SavedData.Factory<>(SettlementManager::new, SettlementManager::load);

    private static final int CURRENT_VERSION = 1;
    private final Map<UUID, SettlementSnapshot> snapshots = new LinkedHashMap<>();

    public static SettlementManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static SettlementManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.migrate(tag, CURRENT_VERSION, "SettlementManager");
        SettlementManager manager = new SettlementManager();
        if (tag.contains("Snapshots", Tag.TAG_LIST)) {
            ListTag snapshots = tag.getList("Snapshots", Tag.TAG_COMPOUND);
            for (Tag entry : snapshots) {
                SettlementSnapshot snapshot = SettlementSnapshot.fromTag((CompoundTag) entry);
                manager.snapshots.put(snapshot.claimUuid(), snapshot);
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.putVersion(tag, CURRENT_VERSION);
        ListTag list = new ListTag();
        for (SettlementSnapshot snapshot : this.snapshots.values()) {
            list.add(snapshot.toTag());
        }
        tag.put("Snapshots", list);
        return tag;
    }

    @Nullable
    public SettlementSnapshot getSnapshot(UUID claimUuid) {
        return this.snapshots.get(claimUuid);
    }

    public void putSnapshot(SettlementSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        SettlementSnapshot previous = this.snapshots.put(snapshot.claimUuid(), snapshot);
        if (!snapshot.equals(previous)) {
            this.setDirty();
        }
    }

    @Nullable
    public SettlementSnapshot removeSnapshot(UUID claimUuid) {
        if (claimUuid == null) {
            return null;
        }
        SettlementSnapshot removed = this.snapshots.remove(claimUuid);
        if (removed != null) {
            this.setDirty();
        }
        return removed;
    }

    public void pruneMissingClaims(Set<UUID> activeClaimUuids) {
        boolean removed = false;
        for (UUID claimUuid : new ArrayList<>(this.snapshots.keySet())) {
            if (!activeClaimUuids.contains(claimUuid)) {
                this.snapshots.remove(claimUuid);
                removed = true;
            }
        }
        if (removed) {
            this.setDirty();
        }
    }

    public Collection<SettlementSnapshot> getAllSnapshots() {
        return this.snapshots.values();
    }
}
