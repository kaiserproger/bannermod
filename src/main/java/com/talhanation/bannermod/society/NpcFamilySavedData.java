package com.talhanation.bannermod.society;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class NpcFamilySavedData extends SavedData {
    private static final String FILE_ID = "bannermodNpcFamily";
    private static final SavedData.Factory<NpcFamilySavedData> FACTORY =
            new SavedData.Factory<>(NpcFamilySavedData::new, NpcFamilySavedData::load);

    private final NpcFamilyRuntime runtime;

    public NpcFamilySavedData() {
        this(new NpcFamilyRuntime());
    }

    private NpcFamilySavedData(NpcFamilyRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::setDirty);
    }

    public static NpcFamilySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static NpcFamilySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new NpcFamilySavedData(NpcFamilyRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag runtimeTag = this.runtime.toTag();
        tag.put("Families", runtimeTag.getList("Families", 10));
        return tag;
    }

    public NpcFamilyRuntime runtime() {
        return this.runtime;
    }
}
