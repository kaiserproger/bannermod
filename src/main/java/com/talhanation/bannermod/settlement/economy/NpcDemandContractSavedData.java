package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.persistence.SavedDataVersioning;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class NpcDemandContractSavedData extends SavedData {
    private static final String FILE_ID = "bannermodNpcDemandContracts";
    private static final SavedData.Factory<NpcDemandContractSavedData> FACTORY = new SavedData.Factory<>(NpcDemandContractSavedData::new, NpcDemandContractSavedData::load);
    private static final int CURRENT_VERSION = 1;

    private final NpcDemandContractService service;

    public NpcDemandContractSavedData() {
        this.service = new NpcDemandContractService(this::setDirty);
    }

    public static NpcDemandContractSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static NpcDemandContractSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.migrate(tag, CURRENT_VERSION, "NpcDemandContractSavedData");
        NpcDemandContractSavedData data = new NpcDemandContractSavedData();
        data.service.loadFromTag(tag);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.putVersion(tag, CURRENT_VERSION);
        this.service.saveToTag(tag);
        return tag;
    }

    public NpcDemandContractService service() {
        return this.service;
    }
}
