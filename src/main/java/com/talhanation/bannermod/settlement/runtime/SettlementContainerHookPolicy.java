package com.talhanation.bannermod.settlement.runtime;

/**
 * Pure decision helper for "should this container place/break trigger a settlement refresh?"
 *
 * <p>Extracted so the hook listener can stay tiny and the actual decision can be unit-tested
 * without spinning up a server level. The current rule: only refresh if the affected block
 * is a {@link net.minecraft.world.Container} and lives inside an authored
 * {@link com.talhanation.bannermod.entity.civilian.workarea.StorageArea}; refreshing on every
 * chest in the world would cost too much.</p>
 */
public final class SettlementContainerHookPolicy {

    private SettlementContainerHookPolicy() {
    }

    public static boolean shouldRefresh(boolean isContainer, boolean insideStorageArea) {
        return isContainer && insideStorageArea;
    }
}
