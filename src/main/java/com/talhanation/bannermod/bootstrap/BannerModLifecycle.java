package com.talhanation.bannermod.bootstrap;

import com.talhanation.bannermod.commands.military.RecruitsAdminCommands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Lifecycle event handlers extracted from legacy recruits.Main and workers.WorkersMain.
 * Additional command/event registration lives here so BannerModMain stays focused on startup wiring.
 */
public class BannerModLifecycle {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RecruitsAdminCommands.register(event.getDispatcher());
    }
}
