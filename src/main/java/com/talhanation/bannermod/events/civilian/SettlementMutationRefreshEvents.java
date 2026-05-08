package com.talhanation.bannermod.events.civilian;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.settlement.runtime.SettlementContainerHookPolicy;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementRefreshSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;

/**
 * Forge event subscriber that triggers a settlement snapshot refresh when a civilian
 * mutation happens outside the existing message/AI hot paths.
 *
 * <p>Three classes of event are covered:</p>
 * <ul>
 *   <li>{@link LivingDeathEvent} for {@link AbstractWorkerEntity} — refresh at the worker's
 *       last known position so its stockpile/work assignments leave the snapshot.</li>
 *   <li>{@link EntityLeaveLevelEvent} for {@link AbstractWorkerEntity} — covers despawn,
 *       chunk-unload, and {@code Entity#remove(...)} paths the death event misses (worker
 *       discarded by an admin command, etc.).</li>
 *   <li>{@link BlockEvent.EntityPlaceEvent} / {@link BlockEvent.BreakEvent} for blocks that
 *       expose a {@link Container} block-entity, gated by
 *       {@link SettlementContainerHookPolicy#shouldRefresh} so distant chests do not pay
 *       the snapshot cost.</li>
 * </ul>
 */
public final class SettlementMutationRefreshEvents {

    private static final double STORAGE_LOOKUP_RADIUS = 12.0D;

    @SubscribeEvent
    public void onWorkerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractWorkerEntity worker)) return;
        if (!(worker.level() instanceof ServerLevel serverLevel)) return;
        BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, worker.blockPosition());
    }

    @SubscribeEvent
    public void onWorkerLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof AbstractWorkerEntity worker)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        // Only refresh on real removals (death/discard), not vanilla chunk-unload churn.
        Entity.RemovalReason reason = worker.getRemovalReason();
        if (reason == null || !reason.shouldDestroy()) return;
        BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, worker.blockPosition());
    }

    @SubscribeEvent
    public void onContainerPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = event.getPos();
        if (pos == null) return;
        boolean isContainer = serverLevel.getBlockEntity(pos) instanceof Container;
        if (!SettlementContainerHookPolicy.shouldRefresh(isContainer, isInsideStorageArea(serverLevel, pos))) return;
        BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, pos);
    }

    @SubscribeEvent
    public void onContainerBroken(BlockEvent.BreakEvent event) {
        LevelAccessor level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos pos = event.getPos();
        if (pos == null) return;
        // The block-entity at pos is still alive in BreakEvent (fires before world removal).
        boolean isContainer = serverLevel.getBlockEntity(pos) instanceof Container;
        if (!SettlementContainerHookPolicy.shouldRefresh(isContainer, isInsideStorageArea(serverLevel, pos))) return;
        BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, pos);
    }

    private static boolean isInsideStorageArea(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        List<StorageArea> nearby = WorkAreaIndex.instance().queryInRange(level, center, STORAGE_LOOKUP_RADIUS, StorageArea.class);
        for (StorageArea area : nearby) {
            if (area == null || !area.isAlive()) continue;
            if (area.getArea() != null && area.getArea().contains(center.x, center.y, center.z)) {
                return true;
            }
        }
        return false;
    }
}
