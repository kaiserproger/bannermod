package com.talhanation.bannermod.events.civilian;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.settlement.SettlementOrchestrator;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrder;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Forge event subscriber that releases an {@link AbstractWorkerEntity}'s active
 * {@link SettlementWorkOrder} claim back to PENDING the instant the worker leaves the world.
 *
 * <p>Without this, a worker that dies mid-route or is force-removed by an admin command keeps
 * its claim parked in CLAIMED until {@link SettlementWorkOrderRuntime#reclaimAbandoned(long)}
 * fires the per-claim expiry timer (default 40 seconds). That delay is gameplay-visible — no
 * other worker can pick up the order and the route silently stalls. Releasing on the death /
 * leave event closes the window so the order is immediately available to the next claimant.</p>
 *
 * <p>{@link #invocationCount()} is a per-process observability seam used by the gametest
 * harness to confirm the live event-bus path was actually exercised.</p>
 */
public final class SettlementWorkOrderClaimReleaseEvents {

    private static final AtomicLong INVOCATIONS = new AtomicLong(0L);

    @SubscribeEvent
    public void onWorkerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractWorkerEntity worker)) return;
        if (!(worker.level() instanceof ServerLevel serverLevel)) return;
        releaseFor(serverLevel, worker.getUUID());
    }

    @SubscribeEvent
    public void onWorkerLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof AbstractWorkerEntity worker)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Entity.RemovalReason reason = worker.getRemovalReason();
        // Skip vanilla chunk-unload churn; only act on real removals (death, discarded, killed).
        if (reason == null || !reason.shouldDestroy()) return;
        releaseFor(serverLevel, worker.getUUID());
    }

    private static void releaseFor(ServerLevel serverLevel, UUID residentUuid) {
        SettlementWorkOrderRuntime runtime = SettlementOrchestrator.workOrderRuntime(serverLevel);
        if (runtime == null) return;
        List<SettlementWorkOrder> released = runtime.releaseClaimsForResident(residentUuid);
        if (!released.isEmpty()) {
            INVOCATIONS.incrementAndGet();
        }
    }

    /** Test seam: count of releases that actually returned an order to PENDING. */
    public static long invocationCount() {
        return INVOCATIONS.get();
    }

    /** Test seam: reset the counter between gametests. */
    public static void resetInvocationCountForTests() {
        INVOCATIONS.set(0L);
    }
}
