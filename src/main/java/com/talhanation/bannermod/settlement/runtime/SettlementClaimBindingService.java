package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.AnimalFarmerEntity;
import com.talhanation.bannermod.entity.civilian.BuilderEntity;
import com.talhanation.bannermod.entity.civilian.FarmerEntity;
import com.talhanation.bannermod.entity.civilian.FishermanEntity;
import com.talhanation.bannermod.entity.civilian.LumberjackEntity;
import com.talhanation.bannermod.entity.civilian.MerchantEntity;
import com.talhanation.bannermod.entity.civilian.MinerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AnimalPenArea;
import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.entity.civilian.workarea.FishingArea;
import com.talhanation.bannermod.entity.civilian.workarea.LumberArea;
import com.talhanation.bannermod.entity.civilian.workarea.MarketArea;
import com.talhanation.bannermod.entity.civilian.workarea.MiningArea;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementService;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the claim-refresh and worker-binding repair pipeline that was previously embedded inside
 * {@link SettlementService}. Snapshot construction and lookup helpers stay on the
 * orchestrator service; this class only handles iterating claims, putting snapshots, and repairing
 * worker bindings against canonical validated buildings.
 */
public final class SettlementClaimBindingService {
    private SettlementClaimBindingService() {
    }

    public static void refreshAllClaims(ServerLevel level,
                                        RecruitsClaimManager claimManager,
                                        SettlementManager settlementManager,
                                        BannerModGovernorManager governorManager) {
        refreshClaimsBatch(level, claimManager, settlementManager, governorManager, 0, Integer.MAX_VALUE);
    }

    public static BatchResult refreshClaimsBatch(ServerLevel level,
                                                 RecruitsClaimManager claimManager,
                                                 SettlementManager settlementManager,
                                                 BannerModGovernorManager governorManager,
                                                 int startIndex,
                                                 int maxClaims) {
        if (level == null || claimManager == null || settlementManager == null) {
            return BatchResult.completedResult();
        }
        long startNanos = System.nanoTime();

        List<RecruitsClaim> claims = new ArrayList<>(claimManager.getAllClaims());
        claims.removeIf(claim -> claim == null || claim.getUUID() == null);
        claims.sort(Comparator.comparing(RecruitsClaim::getUUID));
        int total = claims.size();
        if (total == 0 || maxClaims <= 0) {
            if (total == 0) {
                settlementManager.pruneMissingClaims(Set.of());
            }
            return recordBatchResult("settlement.heartbeat.refresh_batch", new BatchResult(0, total == 0 ? 0 : Math.max(0, Math.min(startIndex, total)), total, total == 0), startNanos);
        }

        int clampedStart = Math.max(0, Math.min(startIndex, total));
        int endIndex = Math.min(total, clampedStart + maxClaims);
        for (int i = clampedStart; i < endIndex; i++) {
            settlementManager.putSnapshot(SettlementService.buildSnapshot(level, claims.get(i), governorManager));
        }

        if (endIndex >= total) {
            Set<UUID> activeClaimUuids = new LinkedHashSet<>();
            for (RecruitsClaim claim : claims) {
                activeClaimUuids.add(claim.getUUID());
            }
            settlementManager.pruneMissingClaims(activeClaimUuids);
        }

        return recordBatchResult("settlement.heartbeat.refresh_batch", new BatchResult(clampedStart, endIndex, total, endIndex >= total), startNanos);
    }

    private static BatchResult recordBatchResult(String keyPrefix, BatchResult result, long startNanos) {
        RuntimeProfilingCounters.recordBatch(keyPrefix, Math.max(0, result.nextIndex() - result.startIndex()), result.totalItems(), System.nanoTime() - startNanos, result.completed());
        return result;
    }

    public record BatchResult(int startIndex,
                              int nextIndex,
                              int totalItems,
                              boolean completed) {
        private static BatchResult completedResult() {
            return new BatchResult(0, 0, 0, true);
        }
    }

    public static void refreshClaimAt(ServerLevel level,
                                      RecruitsClaimManager claimManager,
                                      SettlementManager settlementManager,
                                      BannerModGovernorManager governorManager,
                                      BlockPos pos) {
        if (level == null || claimManager == null || settlementManager == null || pos == null) {
            return;
        }
        refreshClaim(level, claimManager, settlementManager, governorManager, claimManager.getClaim(new ChunkPos(pos)));
    }

    public static void refreshClaim(ServerLevel level,
                                    RecruitsClaimManager claimManager,
                                    SettlementManager settlementManager,
                                    @Nullable BannerModGovernorManager governorManager,
                                    @Nullable RecruitsClaim claim) {
        if (level == null || claimManager == null || settlementManager == null) {
            return;
        }
        if (claim == null) {
            return;
        }
        settlementManager.putSnapshot(SettlementService.buildSnapshot(level, claim, governorManager));
    }

    public static void repairClaimState(ServerLevel level,
                                        RecruitsClaim claim,
                                        List<AbstractWorkAreaEntity> workAreas,
                                        List<ValidatedBuildingRecord> validatedBuildings) {
        if (level == null || claim == null) {
            return;
        }
        Map<UUID, UUID> canonicalBindings = SettlementService.buildCanonicalWorkAreaBindings(validatedBuildings, workAreas);
        Map<UUID, AbstractWorkAreaEntity> areasById = new LinkedHashMap<>();
        for (AbstractWorkAreaEntity workArea : workAreas) {
            areasById.put(workArea.getUUID(), workArea);
        }
        for (AbstractWorkerEntity worker : SettlementService.workersInClaim(level, claim)) {
            repairWorkerBinding(worker, workAreas, canonicalBindings, areasById);
        }
    }

    private static void repairWorkerBinding(AbstractWorkerEntity worker,
                                            List<AbstractWorkAreaEntity> workAreas,
                                            Map<UUID, UUID> canonicalBindings,
                                            Map<UUID, AbstractWorkAreaEntity> areasById) {
        if (worker == null) {
            return;
        }
        UUID currentBinding = worker.getBoundWorkAreaUUID();
        if (currentBinding == null) {
            return;
        }
        List<UUID> compatibleAreaIds = new ArrayList<>();
        for (AbstractWorkAreaEntity workArea : workAreas) {
            if (isCompatibleWorkArea(worker, workArea) && workArea.canWorkHere(worker)) {
                compatibleAreaIds.add(workArea.getUUID());
            }
        }
        UUID repairedBinding = resolveRepairBinding(currentBinding, compatibleAreaIds, canonicalBindings);
        if (repairedBinding == null) {
            worker.setCurrentWorkArea(null);
            worker.clearWorkStatus();
            return;
        }
        AbstractWorkAreaEntity currentArea = worker.getCurrentWorkArea();
        if (currentArea != null && repairedBinding.equals(currentArea.getUUID()) && currentArea.canWorkHere(worker)) {
            return;
        }
        AbstractWorkAreaEntity repairedArea = areasById.get(repairedBinding);
        if (repairedArea == null || !repairedArea.canWorkHere(worker)) {
            worker.setCurrentWorkArea(null);
            worker.clearWorkStatus();
            return;
        }
        worker.setCurrentWorkArea(repairedArea);
        worker.clearWorkStatus();
    }

    public static UUID resolveRepairBinding(@Nullable UUID currentBinding,
                                            List<UUID> compatibleAreaIds,
                                            Map<UUID, UUID> canonicalBindings) {
        if (currentBinding == null) {
            return null;
        }
        UUID canonicalCurrent = canonicalBindings.get(currentBinding);
        if (canonicalCurrent != null) {
            return canonicalCurrent;
        }
        if (compatibleAreaIds.contains(currentBinding)) {
            return currentBinding;
        }
        Set<UUID> distinctCanonicalCandidates = new LinkedHashSet<>();
        for (UUID areaId : compatibleAreaIds) {
            distinctCanonicalCandidates.add(canonicalBindings.getOrDefault(areaId, areaId));
        }
        return distinctCanonicalCandidates.size() == 1 ? distinctCanonicalCandidates.iterator().next() : null;
    }

    private static boolean isCompatibleWorkArea(AbstractWorkerEntity worker, AbstractWorkAreaEntity workArea) {
        if (worker instanceof FarmerEntity) {
            return workArea instanceof CropArea;
        }
        if (worker instanceof MinerEntity) {
            return workArea instanceof MiningArea;
        }
        if (worker instanceof LumberjackEntity) {
            return workArea instanceof LumberArea;
        }
        if (worker instanceof FishermanEntity) {
            return workArea instanceof FishingArea;
        }
        if (worker instanceof MerchantEntity) {
            return workArea instanceof MarketArea;
        }
        if (worker instanceof AnimalFarmerEntity) {
            return workArea instanceof AnimalPenArea;
        }
        if (worker instanceof BuilderEntity) {
            return workArea instanceof BuildArea;
        }
        return false;
    }
}
