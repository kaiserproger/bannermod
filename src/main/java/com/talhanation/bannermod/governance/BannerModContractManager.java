package com.talhanation.bannermod.governance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class BannerModContractManager extends SavedData {
    private static final String FILE_ID = "bannermodContracts";

    private final Map<UUID, BannerModGovernorContract> contracts = new LinkedHashMap<>();

    public static BannerModContractManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BannerModContractManager::load, BannerModContractManager::new, FILE_ID);
    }

    public static BannerModContractManager load(CompoundTag tag) {
        BannerModContractManager manager = new BannerModContractManager();
        if (tag.contains("Contracts", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Contracts", Tag.TAG_COMPOUND);
            for (Tag entry : list) {
                BannerModGovernorContract contract = BannerModGovernorContract.fromTag((CompoundTag) entry);
                manager.contracts.put(contract.contractId(), contract);
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BannerModGovernorContract c : contracts.values()) {
            list.add(c.toTag());
        }
        tag.put("Contracts", list);
        return tag;
    }

    public void putContract(BannerModGovernorContract contract) {
        BannerModGovernorContract previous = contracts.put(contract.contractId(), contract);
        if (!contract.equals(previous)) setDirty();
    }

    @Nullable
    public BannerModGovernorContract getContract(UUID contractId) {
        return contracts.get(contractId);
    }

    public List<BannerModGovernorContract> getContractsForClaim(UUID claimUuid) {
        return contracts.values().stream()
                .filter(c -> c.claimUuid().equals(claimUuid))
                .collect(Collectors.toList());
    }

    public List<BannerModGovernorContract> getOpenContractsForClaim(UUID claimUuid) {
        return contracts.values().stream()
                .filter(c -> c.claimUuid().equals(claimUuid) && c.isOpen())
                .collect(Collectors.toList());
    }

    public Collection<BannerModGovernorContract> getAllContracts() {
        return contracts.values();
    }

    public void expireOldContracts(long currentTick) {
        boolean changed = false;
        for (Map.Entry<UUID, BannerModGovernorContract> entry : new ArrayList<>(contracts.entrySet())) {
            BannerModGovernorContract c = entry.getValue();
            if (c.isExpired(currentTick)) {
                contracts.put(entry.getKey(), c.withStatus(BannerModGovernorContractStatus.EXPIRED));
                changed = true;
            }
        }
        if (changed) setDirty();
    }
}
