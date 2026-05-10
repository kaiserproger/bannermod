package com.talhanation.bannermod.war.events;

import com.talhanation.bannermod.war.WarRuntimeContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class TreatyPaymentTicker {
    private static final int TICK_INTERVAL = 20;

    private int counter = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;
        if (++counter < TICK_INTERVAL) return;
        counter = 0;

        WarRuntimeContext.treatyPayments(level).processDue(level.getGameTime());
    }
}
