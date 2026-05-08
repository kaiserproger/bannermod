package com.talhanation.bannermod;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.bootstrap.WorkersRuntime;
import com.talhanation.bannermod.network.BannerModNetworkBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerModIntegratedRuntimeSmokeTest {

    @Test
    void recruitRuntimeIdentityAndWorkerSubsystemSeamShareOneBannerModRuntime() {
        assertEquals("bannermod", BannerModMain.MOD_ID);
        assertEquals(BannerModMain.MOD_ID, WorkersRuntime.modId());
        assertEquals(BannerModNetworkBootstrap.workerPacketOffset(), WorkersRuntime.networkIdOffset());
        assertEquals(BannerModNetworkBootstrap.MILITARY_MESSAGES.length, BannerModNetworkBootstrap.workerPacketOffset());
        assertEquals(33, BannerModNetworkBootstrap.CIVILIAN_MESSAGES.length);
        assertTrue(BannerModNetworkBootstrap.CIVILIAN_MESSAGES.length > 0);
    }
}
