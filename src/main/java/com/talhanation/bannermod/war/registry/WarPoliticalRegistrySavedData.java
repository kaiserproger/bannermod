package com.talhanation.bannermod.war.registry;

import com.talhanation.bannermod.persistence.SafeSavedDataWriter;
import com.talhanation.bannermod.persistence.SavedDataVersioning;
import com.talhanation.bannermod.war.events.WarSyncDirtyTracker;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class WarPoliticalRegistrySavedData extends SavedData {
    private static final String FILE_ID = "bannermodPoliticalRegistry";
    private static final SavedData.Factory<WarPoliticalRegistrySavedData> FACTORY = new SavedData.Factory<>(WarPoliticalRegistrySavedData::new, WarPoliticalRegistrySavedData::load);

    private static final int CURRENT_VERSION = 1;
    private final PoliticalRegistryRuntime runtime;

    public WarPoliticalRegistrySavedData() {
        this(new PoliticalRegistryRuntime());
    }

    private WarPoliticalRegistrySavedData(PoliticalRegistryRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::markDirty);
    }

    private void markDirty() {
        setDirty();
        WarSyncDirtyTracker.markDirty();
    }

    public static WarPoliticalRegistrySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static WarPoliticalRegistrySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.migrate(tag, CURRENT_VERSION, "WarPoliticalRegistrySavedData");
        return new WarPoliticalRegistrySavedData(PoliticalRegistryRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return SafeSavedDataWriter.write("WarPoliticalRegistry", tag, registries, (out, regs) -> {
            SavedDataVersioning.putVersion(out, CURRENT_VERSION);
            CompoundTag runtimeTag = this.runtime.toTag();
            out.put("PoliticalEntities", runtimeTag.getList("PoliticalEntities", Tag.TAG_COMPOUND));
        });
    }

    public PoliticalRegistryRuntime runtime() {
        return runtime;
    }
}
