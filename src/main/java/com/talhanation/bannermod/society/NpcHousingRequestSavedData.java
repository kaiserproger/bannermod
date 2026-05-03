package com.talhanation.bannermod.society;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class NpcHousingRequestSavedData extends SavedData {
    private static final String FILE_ID = "bannermodNpcHousingRequests";
    private static final SavedData.Factory<NpcHousingRequestSavedData> FACTORY =
            new SavedData.Factory<>(NpcHousingRequestSavedData::new, NpcHousingRequestSavedData::load);

    private final NpcHousingRequestRuntime runtime;

    public NpcHousingRequestSavedData() {
        this(new NpcHousingRequestRuntime());
    }

    private NpcHousingRequestSavedData(NpcHousingRequestRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::setDirty);
    }

    public static NpcHousingRequestSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static NpcHousingRequestSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new NpcHousingRequestSavedData(NpcHousingRequestRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag runtimeTag = this.runtime.toTag();
        tag.put("Requests", runtimeTag.getList("Requests", 10));
        return tag;
    }

    public NpcHousingRequestRuntime runtime() {
        return this.runtime;
    }
}
