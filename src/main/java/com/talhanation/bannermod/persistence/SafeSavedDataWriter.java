package com.talhanation.bannermod.persistence;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import java.util.function.BiConsumer;

/**
 * Wrap a SavedData {@code save} body so a runtime error in our serialization path is logged
 * with the failing SavedData name instead of crashing the world autosave.
 *
 * <p>Minecraft saves levels via a chain that does not catch RuntimeExceptions from SavedData
 * writers — one of our writers throwing aborts the entire autosave and surfaces as a "saving
 * crashed the game" report with little context about which SavedData was at fault. This helper
 * keeps the original tag intact (so a transient bug never bricks the world.dat entry on disk
 * with a half-written payload) and shifts the failure into the log with a clear label.</p>
 */
public final class SafeSavedDataWriter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SafeSavedDataWriter() {
    }

    public static CompoundTag write(String savedDataName,
                                    CompoundTag tag,
                                    HolderLookup.Provider registries,
                                    BiConsumer<CompoundTag, HolderLookup.Provider> body) {
        try {
            body.accept(tag, registries);
        } catch (Throwable t) {
            LOGGER.error("Failed to serialize {} SavedData — autosave continues with last known good payload",
                    savedDataName, t);
        }
        return tag;
    }
}
