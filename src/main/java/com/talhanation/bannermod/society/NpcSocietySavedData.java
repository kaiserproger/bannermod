package com.talhanation.bannermod.society;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class NpcSocietySavedData extends SavedData {
    private static final String FILE_ID = "bannermodNpcSociety";
    private static final SavedData.Factory<NpcSocietySavedData> FACTORY =
            new SavedData.Factory<>(NpcSocietySavedData::new, NpcSocietySavedData::load);

    private final NpcSocietyRuntime runtime;

    public NpcSocietySavedData() {
        this(new NpcSocietyRuntime());
    }

    private NpcSocietySavedData(NpcSocietyRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::setDirty);
    }

    public static NpcSocietySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static NpcSocietySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new NpcSocietySavedData(NpcSocietyRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag runtimeTag = this.runtime.toTag();
        tag.put("Profiles", runtimeTag.getList("Profiles", 10));
        return tag;
    }

    public NpcSocietyRuntime runtime() {
        return this.runtime;
    }
}
