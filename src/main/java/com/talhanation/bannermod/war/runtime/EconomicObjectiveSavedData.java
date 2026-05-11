package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.persistence.SafeSavedDataWriter;
import com.talhanation.bannermod.persistence.SavedDataVersioning;
import com.talhanation.bannermod.war.events.WarSyncDirtyTracker;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class EconomicObjectiveSavedData extends SavedData {
    private static final String FILE_ID = "bannermodEconomicObjectives";
    private static final SavedData.Factory<EconomicObjectiveSavedData> FACTORY = new SavedData.Factory<>(EconomicObjectiveSavedData::new, EconomicObjectiveSavedData::load);
    private static final int CURRENT_VERSION = 1;

    private final EconomicObjectiveRuntime runtime;

    public EconomicObjectiveSavedData() {
        this(new EconomicObjectiveRuntime());
    }

    private EconomicObjectiveSavedData(EconomicObjectiveRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::markDirty);
    }

    public static EconomicObjectiveSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static EconomicObjectiveSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.migrate(tag, CURRENT_VERSION, "EconomicObjectiveSavedData");
        return new EconomicObjectiveSavedData(EconomicObjectiveRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return SafeSavedDataWriter.write("EconomicObjective", tag, registries, (out, regs) -> {
            SavedDataVersioning.putVersion(out, CURRENT_VERSION);
            CompoundTag inner = runtime.toTag();
            out.put("EconomicObjectives", inner.getList("EconomicObjectives", Tag.TAG_COMPOUND));
        });
    }

    public EconomicObjectiveRuntime runtime() {
        return runtime;
    }

    private void markDirty() {
        setDirty();
        WarSyncDirtyTracker.markDirty();
    }
}
