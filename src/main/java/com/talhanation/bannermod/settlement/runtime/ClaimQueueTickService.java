package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.army.command.CommandIntentQueueRuntime;
import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.settlement.validation.BuildingInvalidationRuntime;
import com.talhanation.bannermod.util.AdaptiveRuntimeBudgets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class ClaimQueueTickService {
    private long serverTickStartedAtNanos;

    public void onServerTickStart() {
        serverTickStartedAtNanos = System.nanoTime();
    }

    public void recordServerTickDuration() {
        if (serverTickStartedAtNanos > 0L) {
            AdaptiveRuntimeBudgets.recordServerTickNanos(System.nanoTime() - serverTickStartedAtNanos);
        }
    }

    public void tickCommandQueue(MinecraftServer server, ServerLevel level) {
        // Command-intent queue advancement runs every tick; the runtime no-ops when idle.
        CommandIntentQueueRuntime.instance().tick(server, level.getGameTime());
    }

    public void tickBuildingInvalidationQueue(ServerLevel level) {
        int revalidationBudget = AdaptiveRuntimeBudgets.intBudget(
                "settlement.revalidation.batch",
                WorkersServerConfig.settlementRevalidationBatchSizePerTick(),
                1
        );
        BuildingInvalidationRuntime.tickBatch(level, revalidationBudget);
    }
}
