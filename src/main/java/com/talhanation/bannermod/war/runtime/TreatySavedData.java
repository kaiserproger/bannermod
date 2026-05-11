package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.persistence.SafeSavedDataWriter;
import com.talhanation.bannermod.persistence.SavedDataVersioning;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class TreatySavedData extends SavedData {
    private static final String FILE_ID = "bannermodTreaties";
    private static final SavedData.Factory<TreatySavedData> FACTORY = new SavedData.Factory<>(TreatySavedData::new, TreatySavedData::load);

    private static final int CURRENT_VERSION = 1;
    private final TreatyRuntime runtime;

    public TreatySavedData() {
        this(new TreatyRuntime());
    }

    private TreatySavedData(TreatyRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::setDirty);
    }

    public static TreatySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static TreatySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SavedDataVersioning.migrate(tag, CURRENT_VERSION, "TreatySavedData");
        return new TreatySavedData(TreatyRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return SafeSavedDataWriter.write("Treaty", tag, registries, (out, regs) -> {
            SavedDataVersioning.putVersion(out, CURRENT_VERSION);
            CompoundTag inner = runtime.toTag();
            out.put("TributeTreaties", inner.getList("TributeTreaties", Tag.TAG_COMPOUND));
            out.put("VassalRelationships", inner.getList("VassalRelationships", Tag.TAG_COMPOUND));
            out.put("DefaultFacts", inner.getList("DefaultFacts", Tag.TAG_COMPOUND));
        });
    }

    public TreatyRuntime runtime() {
        return runtime;
    }
}
