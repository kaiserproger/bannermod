package com.talhanation.bannermod.settlement.project;

import com.talhanation.bannermod.persistence.SavedDataVersioning;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class SettlementProjectSavedData extends SavedData {
    private static final String FILE_ID = "bannermodSettlementProjects";
    private static final SavedData.Factory<SettlementProjectSavedData> FACTORY = new SavedData.Factory<>(SettlementProjectSavedData::new, SettlementProjectSavedData::load);

    private static final int CURRENT_VERSION = 1;
    private final SettlementProjectRuntime runtime;

    public SettlementProjectSavedData() {
        this(new SettlementProjectRuntime(
                SettlementProjectScheduler.detached(),
                new BannerModBuildAreaProjectBridge()
        ));
    }

    private SettlementProjectSavedData(SettlementProjectRuntime runtime) {
        this.runtime = runtime;
        this.runtime.scheduler().setDirtyListener(this::setDirty);
    }

    public static SettlementProjectSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static SettlementProjectSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.migrate(tag, CURRENT_VERSION, "SettlementProjectSavedData");
        return new SettlementProjectSavedData(new SettlementProjectRuntime(
                SettlementProjectScheduler.fromTag(tag),
                new BannerModBuildAreaProjectBridge()
        ));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.putVersion(tag, CURRENT_VERSION);
        CompoundTag runtimeTag = this.runtime.scheduler().toTag();
        tag.put("Queues", runtimeTag.getList("Queues", Tag.TAG_COMPOUND));
        tag.put("Cancellations", runtimeTag.getList("Cancellations", Tag.TAG_COMPOUND));
        return tag;
    }

    public SettlementProjectRuntime runtime() {
        return this.runtime;
    }
}
