package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.persistence.SavedDataVersioning;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class StrategicResourceAccountingManager extends SavedData {
    private static final String FILE_ID = "bannermodStrategicResourceAccounting";
    private static final SavedData.Factory<StrategicResourceAccountingManager> FACTORY = new SavedData.Factory<>(StrategicResourceAccountingManager::new, StrategicResourceAccountingManager::load);

    private static final int CURRENT_VERSION = 1;
    private final Map<UUID, StrategicResourceAccountSnapshot> accounts = new LinkedHashMap<>();

    public static StrategicResourceAccountingManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static StrategicResourceAccountingManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.migrate(tag, CURRENT_VERSION, "StrategicResourceAccountingManager");
        StrategicResourceAccountingManager manager = new StrategicResourceAccountingManager();
        if (tag.contains("Accounts", Tag.TAG_LIST)) {
            ListTag accounts = tag.getList("Accounts", Tag.TAG_COMPOUND);
            for (Tag entry : accounts) {
                StrategicResourceAccountSnapshot account = StrategicResourceAccountSnapshot.fromTag((CompoundTag) entry);
                manager.accounts.put(account.claimUuid(), account);
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.putVersion(tag, CURRENT_VERSION);
        ListTag list = new ListTag();
        for (StrategicResourceAccountSnapshot account : this.accounts.values()) {
            list.add(account.toTag());
        }
        tag.put("Accounts", list);
        return tag;
    }

    @Nullable
    public StrategicResourceAccountSnapshot getAccount(UUID claimUuid) {
        return this.accounts.get(claimUuid);
    }

    public StrategicResourceAccountSnapshot credit(UUID claimUuid, StrategicResourceBucket bucket, int amount, long tick) {
        StrategicResourceAccountSnapshot account = this.accounts.getOrDefault(claimUuid, StrategicResourceAccountSnapshot.empty(claimUuid));
        StrategicResourceAccountSnapshot updated = account.withCredit(bucket, amount, tick);
        putAccount(updated);
        return updated;
    }

    @Nullable
    public StrategicResourceAccountSnapshot debit(UUID claimUuid, StrategicResourceBucket bucket, int amount, long tick) {
        StrategicResourceAccountSnapshot account = this.accounts.get(claimUuid);
        if (account == null) {
            return null;
        }
        StrategicResourceAccountSnapshot updated = account.withDebit(bucket, amount, tick);
        putAccount(updated);
        return updated;
    }

    public void putAccount(StrategicResourceAccountSnapshot account) {
        if (account == null) {
            return;
        }
        StrategicResourceAccountSnapshot previous = this.accounts.put(account.claimUuid(), account);
        if (!account.equals(previous)) {
            this.setDirty();
        }
    }

    public Collection<StrategicResourceAccountSnapshot> getAllAccounts() {
        return this.accounts.values();
    }
}
