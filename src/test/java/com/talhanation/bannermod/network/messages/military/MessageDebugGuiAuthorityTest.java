package com.talhanation.bannermod.network.messages.military;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDebugGuiAuthorityTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000a01");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000a02");

    @Test
    void nonOwnerNonOpCannotUseDebugScreenToHealKillOrDisbandForeignRecruit() {
        int[] mutatingDebugActions = {14, 15, 26};

        for (int debugAction : mutatingDebugActions) {
            assertFalse(
                    MessageDebugGui.shouldHandleDebugMessage(debugAction, OUTSIDER, null, false, OWNER, null, true),
                    "debug action " + debugAction + " must require owner/direct-control or op authority"
            );
        }
    }

    @Test
    void ownerAndOpCanUseDebugScreen() {
        assertTrue(MessageDebugGui.shouldHandleDebugMessage(14, OWNER, null, false, OWNER, null, true));
        assertTrue(MessageDebugGui.shouldHandleDebugMessage(15, OUTSIDER, null, true, OWNER, null, true));
    }
}
