package com.talhanation.bannermod.settlement.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementContainerHookPolicyTest {

    @Test
    void containerInsideStorageAreaTriggersRefresh() {
        assertTrue(SettlementContainerHookPolicy.shouldRefresh(true, true));
    }

    @Test
    void containerOutsideStorageAreaIsIgnored() {
        assertFalse(SettlementContainerHookPolicy.shouldRefresh(true, false));
    }

    @Test
    void nonContainerInsideStorageAreaIsIgnored() {
        assertFalse(SettlementContainerHookPolicy.shouldRefresh(false, true));
    }

    @Test
    void nonContainerOutsideStorageAreaIsIgnored() {
        assertFalse(SettlementContainerHookPolicy.shouldRefresh(false, false));
    }
}
