package com.talhanation.bannermod.network.messages.military;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTeleportPlayerTest {

    @Test
    void nonAuthorizedTeleportPacketsAreDeniedByServerPolicy() {
        assertFalse(MessageTeleportPlayer.isAuthorized(false, false));
        assertFalse(MessageTeleportPlayer.isAuthorized(true, false));
        assertFalse(MessageTeleportPlayer.isAuthorized(false, true));
    }

    @Test
    void creativeOperatorsAreAuthorizedByServerPolicy() {
        assertTrue(MessageTeleportPlayer.isAuthorized(true, true));
    }

    @Test
    void lowHeightmapResultIsCorrectedBeforeLandingOffset() {
        BlockPos target = MessageTeleportPlayer.correctSafeTeleportTarget(new BlockPos(12, -66, 34), -64);

        assertEquals(new BlockPos(12, 100, 34), target);
    }

    @Test
    void normalHeightmapResultKeepsTwoBlockLandingClearance() {
        BlockPos target = MessageTeleportPlayer.correctSafeTeleportTarget(new BlockPos(12, 70, 34), -64);

        assertEquals(new BlockPos(12, 72, 34), target);
    }
}
