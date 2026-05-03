package com.talhanation.bannermod.society;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class NpcHouseholdSavedData extends SavedData {
    private static final String FILE_ID = "bannermodNpcHouseholds";
    private static final SavedData.Factory<NpcHouseholdSavedData> FACTORY =
            new SavedData.Factory<>(NpcHouseholdSavedData::new, NpcHouseholdSavedData::load);

    private final NpcHouseholdRuntime runtime;

    public NpcHouseholdSavedData() {
        this(new NpcHouseholdRuntime());
    }

    private NpcHouseholdSavedData(NpcHouseholdRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::setDirty);
    }

    public static NpcHouseholdSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static NpcHouseholdSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new NpcHouseholdSavedData(NpcHouseholdRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag runtimeTag = this.runtime.toTag();
        tag.put("Households", runtimeTag.getList("Households", 10));
        return tag;
    }

    public NpcHouseholdRuntime runtime() {
        return this.runtime;
    }
}
