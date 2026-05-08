package com.talhanation.bannermod.citizen.runtime;

import com.talhanation.bannermod.ai.pathfinding.async.TrueAsyncPathfindingRuntime;
import com.talhanation.bannermod.config.RecruitsServerConfig;
import net.minecraft.server.level.ServerLevel;

public final class CitizenWorldLifecycleService {
    private CitizenWorldLifecycleService() {
    }

    public static void tickAsyncPathfinding(ServerLevel serverLevel) {
        if (serverLevel == null || !RecruitsServerConfig.UseTrueAsyncPathfinding.get()) {
            return;
        }
        if (serverLevel.getServer().overworld() != serverLevel) {
            return;
        }
        TrueAsyncPathfindingRuntime.instance().tick(serverLevel);
    }
}
