package com.talhanation.bannermod.society;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class NpcLivelihoodRequestSavedData extends SavedData {
    private static final String FILE_ID = "bannermodNpcLivelihoodRequests";
    private static final SavedData.Factory<NpcLivelihoodRequestSavedData> FACTORY =
            new SavedData.Factory<>(NpcLivelihoodRequestSavedData::new, NpcLivelihoodRequestSavedData::load);

    private final NpcLivelihoodRequestRuntime runtime;

    public NpcLivelihoodRequestSavedData() {
        this(new NpcLivelihoodRequestRuntime());
    }

    private NpcLivelihoodRequestSavedData(NpcLivelihoodRequestRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::setDirty);
    }

    public static NpcLivelihoodRequestSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static NpcLivelihoodRequestSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new NpcLivelihoodRequestSavedData(NpcLivelihoodRequestRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag serialized = this.runtime.toTag();
        tag.merge(serialized);
        return tag;
    }

    public NpcLivelihoodRequestRuntime runtime() {
        return this.runtime;
    }
}
