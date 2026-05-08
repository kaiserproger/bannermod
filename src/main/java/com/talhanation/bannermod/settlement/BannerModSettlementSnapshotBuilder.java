package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.bootstrap.SettlementRecord;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsRuntime;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeEntrypoint;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class BannerModSettlementSnapshotBuilder {

    private BannerModSettlementSnapshotBuilder() {
    }

    static BannerModSettlementSnapshot buildSnapshot(ServerLevel level,
                                                     RecruitsClaim claim,
                                                     @Nullable BannerModGovernorManager governorManager) {
        ChunkPos anchorChunk = BannerModSettlementSnapshotRuntime.resolveAnchorChunk(claim);
        BannerModGovernorSnapshot governorSnapshot = governorManager == null ? null : governorManager.getSnapshot(claim.getUUID());
        String settlementFactionId = null;
        if (claim.getOwnerPoliticalEntityId() != null) {
            settlementFactionId = claim.getOwnerPoliticalEntityId().toString();
        } else if (governorSnapshot != null) {
            settlementFactionId = governorSnapshot.settlementFactionId();
        }

        List<AbstractWorkAreaEntity> workAreas = BannerModSettlementSnapshotRuntime.collectWorkAreas(level, claim, AbstractWorkAreaEntity.class);
        SettlementRecord settlementRecord = BannerModSettlementSnapshotRuntime.settlementRecordForClaim(level, claim);
        List<ValidatedBuildingRecord> validatedBuildings = BannerModSettlementSnapshotRuntime.collectValidatedBuildings(level, settlementRecord);
        BannerModSettlementSnapshotRuntime.repairClaimState(level, claim, workAreas, validatedBuildings);

        List<BannerModSettlementResidentRecord> residents = BannerModSettlementSnapshotRuntime.collectResidents(level, claim, governorSnapshot, settlementFactionId);
        List<BannerModSettlementBuildingRecord> buildings = BannerModSettlementSnapshotRuntime.collectBuildings(level, claim);
        BannerModSettlementMarketState marketState = BannerModSettlementSnapshotRuntime.collectMarketState(level, claim);
        List<StorageArea> storageAreas = BannerModSettlementSnapshotRuntime.collectStorageAreas(level, claim);
        List<BannerModSeaTradeEntrypoint> liveSeaTradeEntrypoints = BannerModSettlementSnapshotRuntime.collectLiveSeaTradeEntrypoints(storageAreas);
        List<BannerModSeaTradeExecutionRecord> localSeaTradeExecutions = BannerModSettlementSnapshotRuntime.collectLocalSeaTradeExecutions(level, storageAreas);

        Set<UUID> localBuildingUuids = new LinkedHashSet<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            localBuildingUuids.add(building.buildingUuid());
        }
        BannerModSettlementResidentStaffingService.StaffingResult staffing = BannerModSettlementResidentStaffingService.apply(
                residents,
                buildings,
                marketState,
                localBuildingUuids
        );
        BannerModSettlementLogisticsDerivationService.LogisticsResult logistics = BannerModSettlementLogisticsDerivationService.derive(
                staffing.buildings(),
                staffing.residents(),
                staffing.marketState(),
                liveSeaTradeEntrypoints,
                BannerModSettlementSnapshotRuntime.collectLocalLogisticsRoutes(storageAreas),
                BannerModLogisticsRuntime.service().listReservations(),
                localSeaTradeExecutions,
                governorSnapshot != null && governorSnapshot.governorRecruitUuid() != null,
                settlementFactionId != null && !settlementFactionId.isBlank()
        );

        SnapshotCounts counts = summarizeCounts(staffing.buildings(), staffing.residents());
        return new BannerModSettlementSnapshot(
                claim.getUUID(),
                anchorChunk.x,
                anchorChunk.z,
                settlementFactionId,
                level.getGameTime(),
                counts.residentCapacity(),
                counts.workplaceCapacity(),
                counts.assignedWorkerCount(),
                counts.assignedResidentCount(),
                counts.unassignedWorkerCount(),
                counts.missingWorkAreaAssignmentCount(),
                logistics.stockpileSummary(),
                staffing.marketState(),
                logistics.desiredGoodsSnapshot(),
                logistics.projectCandidateSnapshot(),
                logistics.tradeRouteHandoffSnapshot(),
                logistics.supplySignalState(),
                staffing.residents(),
                staffing.buildings()
        );
    }

    private static SnapshotCounts summarizeCounts(List<BannerModSettlementBuildingRecord> buildings,
                                                  List<BannerModSettlementResidentRecord> residents) {
        int residentCapacity = 0;
        int workplaceCapacity = 0;
        int assignedWorkerCount = 0;
        int assignedResidentCount = 0;
        int unassignedWorkerCount = 0;
        int missingWorkAreaAssignmentCount = 0;
        for (BannerModSettlementBuildingRecord building : buildings) {
            residentCapacity += Math.max(0, building.residentCapacity());
            workplaceCapacity += Math.max(0, building.workplaceSlots());
            assignedWorkerCount += Math.max(0, building.assignedWorkerCount());
        }
        for (BannerModSettlementResidentRecord resident : residents) {
            if (resident.role() != BannerModSettlementResidentRole.CONTROLLED_WORKER) {
                continue;
            }
            switch (resident.assignmentState()) {
                case ASSIGNED_LOCAL_BUILDING -> assignedResidentCount++;
                case UNASSIGNED -> unassignedWorkerCount++;
                case ASSIGNED_MISSING_BUILDING -> missingWorkAreaAssignmentCount++;
                default -> {
                }
            }
        }
        return new SnapshotCounts(
                residentCapacity,
                workplaceCapacity,
                assignedWorkerCount,
                assignedResidentCount,
                unassignedWorkerCount,
                missingWorkAreaAssignmentCount
        );
    }

    private record SnapshotCounts(int residentCapacity,
                                  int workplaceCapacity,
                                  int assignedWorkerCount,
                                  int assignedResidentCount,
                                  int unassignedWorkerCount,
                                  int missingWorkAreaAssignmentCount) {
    }
}
