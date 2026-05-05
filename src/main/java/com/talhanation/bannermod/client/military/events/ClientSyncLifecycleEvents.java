package com.talhanation.bannermod.client.military.events;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.society.client.NpcHamletClientState;
import com.talhanation.bannermod.society.client.NpcHousingClientState;
import com.talhanation.bannermod.war.client.WarClientState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BannerModMain.MOD_ID, value = Dist.CLIENT)
public class ClientSyncLifecycleEvents {

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientManager.resetSynchronizedState();
        WarClientState.clear();
        NpcHousingClientState.clear();
        NpcHamletClientState.clear();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientManager.resetSynchronizedState();
        WarClientState.clear();
        NpcHousingClientState.clear();
        NpcHamletClientState.clear();
    }
}
