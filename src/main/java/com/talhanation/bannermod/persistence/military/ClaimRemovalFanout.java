package com.talhanation.bannermod.persistence.military;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.war.WarRuntimeContext;
import com.talhanation.bannermod.war.runtime.OccupationRuntime;
import com.talhanation.bannermod.war.runtime.RevoltRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Centralized cleanup invoked when a {@link RecruitsClaim} is removed (player packet,
 * admin command, or programmatic). Bridges every per-claim subsystem so deleted claims
 * do not leave dangling treasury ledgers, settlement snapshots, governor snapshots,
 * worker bindings, occupations, or revolts.
 *
 * <p>The fanout is idempotent: calling it twice on the same UUID has the same observable
 * effect as calling it once. Subsystems may be {@code null} (manager unavailable) and the
 * fanout will skip them silently rather than fail the whole removal.
 */
public final class ClaimRemovalFanout {
    private ClaimRemovalFanout() {
    }

    /**
     * Server-wired entry point: derives every manager from the level and runs the
     * cleanup against the supplied claim. Must be called BEFORE the claim is removed
     * from {@link RecruitsClaimManager}'s chunk map; the chunk list on the claim itself
     * is still consulted, so callers may pass a claim that has already been detached
     * from the manager — the chunks travel on the {@code RecruitsClaim} value.
     */
    public static FanoutResult onClaimRemoved(@Nullable ServerLevel level, @Nullable RecruitsClaim claim) {
        if (claim == null) {
            return FanoutResult.empty();
        }
        return onClaimRemoved(level, claim.getUUID(), claim.getClaimedChunks());
    }

    public static FanoutResult onClaimRemoved(@Nullable ServerLevel level,
                                              @Nullable UUID claimUuid,
                                              @Nullable List<ChunkPos> claimChunks) {
        if (level == null || claimUuid == null) {
            return FanoutResult.empty();
        }
        BannerModTreasuryManager treasury = BannerModTreasuryManager.get(level);
        SettlementManager settlements = SettlementManager.get(level);
        BannerModGovernorManager governors = BannerModGovernorManager.get(level);
        OccupationRuntime occupations = WarRuntimeContext.occupations(level);
        RevoltRuntime revolts = WarRuntimeContext.revolts(level);

        return apply(claimUuid, claimChunks, treasury, settlements, governors,
                occupations, revolts, workersOnLevel(level));
    }

    /**
     * Pure cleanup primitive: useful for unit tests that hand-build the managers.
     * Performs the same idempotent fanout against the supplied dependencies; any
     * dependency may be {@code null}.
     */
    public static FanoutResult apply(@Nullable UUID claimUuid,
                                     @Nullable List<ChunkPos> claimChunks,
                                     @Nullable BannerModTreasuryManager treasury,
                                     @Nullable SettlementManager settlements,
                                     @Nullable BannerModGovernorManager governors,
                                     @Nullable OccupationRuntime occupations,
                                     @Nullable RevoltRuntime revolts,
                                     @Nullable List<AbstractWorkerEntity> workers) {
        if (claimUuid == null) {
            return FanoutResult.empty();
        }
        boolean treasuryRemoved = false;
        boolean settlementRemoved = false;
        boolean governorRemoved = false;
        int workersDetached = 0;
        List<UUID> removedOccupations = List.of();
        List<UUID> removedRevolts = List.of();

        // Detach worker bindings FIRST. The unbind path triggers a settlement-snapshot
        // refresh through SettlementService, which would otherwise re-seed the
        // snapshot we are about to drop. Doing this before the snapshot/treasury wipes
        // keeps every removal visible in the same tick.
        if (workers != null && claimChunks != null && !claimChunks.isEmpty()) {
            Set<ChunkPos> chunkSet = new HashSet<>(claimChunks);
            for (AbstractWorkerEntity worker : workers) {
                if (worker == null || worker.getBoundWorkAreaUUID() == null) {
                    continue;
                }
                if (!isWorkerBoundInsideClaim(worker, chunkSet)) {
                    continue;
                }
                worker.setCurrentWorkArea(null);
                workersDetached++;
            }
        }
        if (treasury != null) {
            treasuryRemoved = treasury.removeLedger(claimUuid) != null;
        }
        if (settlements != null) {
            settlementRemoved = settlements.removeSnapshot(claimUuid) != null;
        }
        if (governors != null) {
            governorRemoved = governors.removeSnapshot(claimUuid) != null;
        }
        if (occupations != null) {
            removedOccupations = occupations.removeForClaim(claimChunks);
        }
        if (revolts != null && !removedOccupations.isEmpty()) {
            removedRevolts = revolts.removeForOccupations(removedOccupations);
        }
        return new FanoutResult(treasuryRemoved, settlementRemoved, governorRemoved,
                workersDetached, removedOccupations, removedRevolts);
    }

    private static boolean isWorkerBoundInsideClaim(AbstractWorkerEntity worker, Set<ChunkPos> chunkSet) {
        // Resolve the bound work-area entity and test its chunk against the claim.
        // If the bound work area is unloaded or missing, fall back to the worker's
        // own chunk position — workers stand inside their work area's claim under
        // normal play, so this is a safe approximation that still releases stale bindings.
        AbstractWorkAreaEntity area = worker.getCurrentWorkArea();
        ChunkPos sample;
        if (area != null) {
            sample = area.chunkPosition();
        } else {
            sample = worker.chunkPosition();
        }
        return chunkSet.contains(sample);
    }

    private static List<AbstractWorkerEntity> workersOnLevel(ServerLevel level) {
        List<AbstractWorkerEntity> workers = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractWorkerEntity worker && worker.isAlive()) {
                workers.add(worker);
            }
        }
        return workers;
    }

    public record FanoutResult(boolean treasuryLedgerRemoved,
                               boolean settlementSnapshotRemoved,
                               boolean governorSnapshotRemoved,
                               int workersDetached,
                               List<UUID> removedOccupationIds,
                               List<UUID> removedRevoltIds) {
        public static FanoutResult empty() {
            return new FanoutResult(false, false, false, 0, List.of(), List.of());
        }
    }
}
