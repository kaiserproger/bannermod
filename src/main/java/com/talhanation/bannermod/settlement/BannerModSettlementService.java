package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.WorkerIndex;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.entity.civilian.workarea.LumberArea;
import com.talhanation.bannermod.entity.civilian.workarea.MarketArea;
import com.talhanation.bannermod.entity.civilian.workarea.MiningArea;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.entity.civilian.workarea.WorkAreaIndex;
import com.talhanation.bannermod.settlement.runtime.SettlementClaimBindingService;
import com.talhanation.bannermod.settlement.runtime.SettlementSeaTradeAnalyzer;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.settlement.bootstrap.SettlementRecord;
import com.talhanation.bannermod.settlement.bootstrap.SettlementRegistryData;
import com.talhanation.bannermod.settlement.building.BuildingType;
import com.talhanation.bannermod.settlement.building.BuildingValidationState;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRegistryData;
import com.talhanation.bannermod.settlement.prefab.staffing.PrefabAutoStaffingRuntime;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsReservation;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsRoute;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsRuntime;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeEntrypoint;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionRecord;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeExecutionSavedData;
import com.talhanation.bannermod.shared.logistics.BannerModSeaTradeSummary;
import com.talhanation.bannermod.util.RuntimeProfilingCounters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BannerModSettlementService {
    private BannerModSettlementService() {
    }

    public static void refreshAllClaims(ServerLevel level,
                                        RecruitsClaimManager claimManager,
                                        BannerModSettlementManager settlementManager,
                                        BannerModGovernorManager governorManager) {
        SettlementClaimBindingService.refreshAllClaims(level, claimManager, settlementManager, governorManager);
    }

    public static SettlementClaimBindingService.BatchResult refreshClaimsBatch(ServerLevel level,
                                                                               RecruitsClaimManager claimManager,
                                                                               BannerModSettlementManager settlementManager,
                                                                               BannerModGovernorManager governorManager,
                                                                               int startIndex,
                                                                               int maxClaims) {
        return SettlementClaimBindingService.refreshClaimsBatch(level, claimManager, settlementManager, governorManager, startIndex, maxClaims);
    }

    public static void refreshClaimAt(ServerLevel level,
                                      RecruitsClaimManager claimManager,
                                      BannerModSettlementManager settlementManager,
                                      BannerModGovernorManager governorManager,
                                      BlockPos pos) {
        SettlementClaimBindingService.refreshClaimAt(level, claimManager, settlementManager, governorManager, pos);
    }

    public static void refreshClaim(ServerLevel level,
                                    RecruitsClaimManager claimManager,
                                    BannerModSettlementManager settlementManager,
                                    @Nullable BannerModGovernorManager governorManager,
                                    @Nullable RecruitsClaim claim) {
        SettlementClaimBindingService.refreshClaim(level, claimManager, settlementManager, governorManager, claim);
    }

    public static BannerModSettlementSnapshot buildSnapshot(ServerLevel level,
                                                            RecruitsClaim claim,
                                                            @Nullable BannerModGovernorManager governorManager) {
        return BannerModSettlementSnapshotBuilder.buildSnapshot(level, claim, governorManager);
    }

    static void repairClaimState(ServerLevel level,
                                 RecruitsClaim claim,
                                 List<AbstractWorkAreaEntity> workAreas,
                                 List<ValidatedBuildingRecord> validatedBuildings) {
        SettlementClaimBindingService.repairClaimState(level, claim, workAreas, validatedBuildings);
    }

    static List<BannerModSettlementResidentRecord> collectResidents(ServerLevel level,
                                                                    RecruitsClaim claim,
                                                                    @Nullable BannerModGovernorSnapshot governorSnapshot,
                                                                    @Nullable String settlementFactionId) {
        Map<UUID, BannerModSettlementResidentRecord> residents = new LinkedHashMap<>();
        for (Villager villager : level.getEntitiesOfClass(Villager.class, claimBounds(level, claim), entity -> entity.isAlive() && claim.containsChunk(entity.chunkPosition()))) {
            residents.put(villager.getUUID(), new BannerModSettlementResidentRecord(
                    villager.getUUID(),
                    BannerModSettlementResidentRole.VILLAGER,
                    BannerModSettlementResidentScheduleSeed.SETTLEMENT_IDLE,
                    BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX,
                    BannerModSettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                    BannerModSettlementResidentServiceContract.notServiceActor(),
                    BannerModSettlementResidentJobDefinition.defaultFor(
                            BannerModSettlementResidentRole.VILLAGER,
                            BannerModSettlementResidentRuntimeRoleState.VILLAGE_LIFE,
                            BannerModSettlementResidentServiceContract.notServiceActor(),
                            null
                    ),
                    BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                    null,
                    villager.getTeam() == null ? settlementFactionId : villager.getTeam().getName(),
                    null,
                    BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
            ));
        }
        for (AbstractWorkerEntity worker : workersInClaim(level, claim)) {
            BannerModSettlementResidentScheduleSeed scheduleSeed = BannerModSettlementResidentScheduleSeed.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, worker.getBoundWorkAreaUUID());
            BannerModSettlementResidentMode residentMode = BannerModSettlementResidentMode.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, worker.getOwnerUUID());
            BannerModSettlementResidentAssignmentState assignmentState = worker.getBoundWorkAreaUUID() == null
                    ? BannerModSettlementResidentAssignmentState.UNASSIGNED
                    : BannerModSettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING;
            BannerModSettlementResidentRuntimeRoleState runtimeRoleState = BannerModSettlementResidentRuntimeRoleState.defaultFor(
                    BannerModSettlementResidentRole.CONTROLLED_WORKER,
                    scheduleSeed,
                    residentMode,
                    assignmentState
            );
            residents.put(worker.getUUID(), new BannerModSettlementResidentRecord(
                    worker.getUUID(),
                    BannerModSettlementResidentRole.CONTROLLED_WORKER,
                    scheduleSeed,
                    BannerModSettlementResidentScheduleWindowSeed.defaultFor(scheduleSeed, runtimeRoleState),
                    runtimeRoleState,
                    BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, residentMode, assignmentState, worker.getBoundWorkAreaUUID(), null),
                    BannerModSettlementResidentJobDefinition.defaultFor(
                            BannerModSettlementResidentRole.CONTROLLED_WORKER,
                            runtimeRoleState,
                            BannerModSettlementResidentServiceContract.defaultFor(BannerModSettlementResidentRole.CONTROLLED_WORKER, residentMode, assignmentState, worker.getBoundWorkAreaUUID(), null),
                            null
                    ),
                    residentMode,
                    worker.getOwnerUUID(),
                    worker.getTeam() == null ? null : worker.getTeam().getName(),
                    worker.getBoundWorkAreaUUID(),
                    assignmentState
            ));
        }
        if (governorSnapshot != null && governorSnapshot.governorRecruitUuid() != null) {
            residents.put(governorSnapshot.governorRecruitUuid(), new BannerModSettlementResidentRecord(
                    governorSnapshot.governorRecruitUuid(),
                    BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                    BannerModSettlementResidentScheduleSeed.GOVERNING,
                    BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY,
                    BannerModSettlementResidentRuntimeRoleState.GOVERNANCE,
                    BannerModSettlementResidentServiceContract.notServiceActor(),
                    BannerModSettlementResidentJobDefinition.defaultFor(
                            BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                            BannerModSettlementResidentRuntimeRoleState.GOVERNANCE,
                            BannerModSettlementResidentServiceContract.notServiceActor(),
                            null
                    ),
                    BannerModSettlementResidentMode.SETTLEMENT_RESIDENT,
                    governorSnapshot.governorOwnerUuid(),
                    settlementFactionId,
                    null,
                    BannerModSettlementResidentAssignmentState.NOT_APPLICABLE
            ));
        }
        return new ArrayList<>(residents.values());
    }

    public static List<AbstractWorkerEntity> workersInClaim(ServerLevel level, RecruitsClaim claim) {
        return WorkerIndex.instance()
                .queryInClaim(level, claim)
                .orElseGet(() -> {
                    RuntimeProfilingCounters.increment("worker.index.fallback_scans");
                    return level.getEntitiesOfClass(AbstractWorkerEntity.class, claimBounds(level, claim), entity -> entity.isAlive() && claim.containsChunk(entity.chunkPosition()));
                });
    }

    static List<BannerModSettlementResidentRecord> applyResidentAssignmentSemantics(List<BannerModSettlementResidentRecord> residents,
                                                                                    Set<UUID> localBuildingUuids) {
        if (residents.isEmpty()) {
            return List.of();
        }

        List<BannerModSettlementResidentRecord> updatedResidents = new ArrayList<>(residents.size());
        for (BannerModSettlementResidentRecord resident : residents) {
            if (resident.role() != BannerModSettlementResidentRole.CONTROLLED_WORKER) {
                updatedResidents.add(resident);
                continue;
            }

            BannerModSettlementResidentAssignmentState assignmentState;
            if (resident.boundWorkAreaUuid() == null) {
                assignmentState = BannerModSettlementResidentAssignmentState.UNASSIGNED;
            } else if (localBuildingUuids.contains(resident.boundWorkAreaUuid())) {
                assignmentState = BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING;
            } else {
                assignmentState = BannerModSettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING;
            }

            BannerModSettlementResidentRuntimeRoleState runtimeRoleState = BannerModSettlementResidentRuntimeRoleState.defaultFor(
                    resident.role(),
                    resident.scheduleSeed(),
                    resident.residentMode(),
                    assignmentState
            );
            BannerModSettlementResidentScheduleWindowSeed scheduleWindowSeed = BannerModSettlementResidentScheduleWindowSeed.defaultFor(
                    resident.scheduleSeed(),
                    runtimeRoleState
            );

            updatedResidents.add(new BannerModSettlementResidentRecord(
                    resident.residentUuid(),
                    resident.role(),
                    resident.scheduleSeed(),
                    scheduleWindowSeed,
                    runtimeRoleState,
                    resident.serviceContract(),
                    resident.jobDefinition(),
                    resident.jobTargetSelectionState(),
                    resident.residentMode(),
                    resident.ownerUuid(),
                    resident.teamId(),
                    resident.boundWorkAreaUuid(),
                    assignmentState,
                    BannerModSettlementResidentRoleProfile.defaultFor(
                            resident.role(),
                            runtimeRoleState,
                            resident.residentMode(),
                            assignmentState
                    )
            ));
        }
        return updatedResidents;
    }

    static List<BannerModSettlementResidentRecord> applyResidentServiceContracts(List<BannerModSettlementResidentRecord> residents,
                                                                                 List<BannerModSettlementBuildingRecord> buildings) {
        if (residents.isEmpty()) {
            return List.of();
        }

        Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            buildingsByUuid.put(building.buildingUuid(), building);
        }

        List<BannerModSettlementResidentRecord> updatedResidents = new ArrayList<>(residents.size());
        for (BannerModSettlementResidentRecord resident : residents) {
            BannerModSettlementBuildingRecord serviceBuilding = resident.boundWorkAreaUuid() == null
                    ? null
                    : buildingsByUuid.get(resident.boundWorkAreaUuid());
            BannerModSettlementResidentServiceContract serviceContract = BannerModSettlementResidentServiceContract.defaultFor(
                    resident.role(),
                    resident.residentMode(),
                    resident.assignmentState(),
                    resident.boundWorkAreaUuid(),
                    serviceBuilding == null ? null : serviceBuilding.buildingTypeId()
            );
            updatedResidents.add(new BannerModSettlementResidentRecord(
                    resident.residentUuid(),
                    resident.role(),
                    resident.scheduleSeed(),
                    resident.scheduleWindowSeed(),
                    resident.runtimeRoleState(),
                    serviceContract,
                    resident.jobDefinition(),
                    resident.jobTargetSelectionState(),
                    resident.residentMode(),
                    resident.ownerUuid(),
                    resident.teamId(),
                    resident.boundWorkAreaUuid(),
                    resident.assignmentState(),
                    resident.roleProfile()
            ));
        }
        return updatedResidents;
    }

    static List<BannerModSettlementResidentRecord> applyResidentJobDefinitions(List<BannerModSettlementResidentRecord> residents,
                                                                               List<BannerModSettlementBuildingRecord> buildings) {
        if (residents.isEmpty()) {
            return List.of();
        }

        Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            buildingsByUuid.put(building.buildingUuid(), building);
        }

        List<BannerModSettlementResidentRecord> updatedResidents = new ArrayList<>(residents.size());
        for (BannerModSettlementResidentRecord resident : residents) {
            BannerModSettlementBuildingRecord targetBuilding = resident.serviceContract().serviceBuildingUuid() == null
                    ? null
                    : buildingsByUuid.get(resident.serviceContract().serviceBuildingUuid());
            BannerModSettlementResidentJobDefinition jobDefinition = BannerModSettlementResidentJobDefinition.defaultFor(
                    resident.role(),
                    resident.runtimeRoleState(),
                    resident.serviceContract(),
                    targetBuilding
            );
            updatedResidents.add(new BannerModSettlementResidentRecord(
                    resident.residentUuid(),
                    resident.role(),
                    resident.scheduleSeed(),
                    resident.scheduleWindowSeed(),
                    resident.runtimeRoleState(),
                    resident.serviceContract(),
                    jobDefinition,
                    resident.jobTargetSelectionState(),
                    resident.residentMode(),
                    resident.ownerUuid(),
                    resident.teamId(),
                    resident.boundWorkAreaUuid(),
                    resident.assignmentState(),
                    resident.roleProfile()
            ));
        }
        return updatedResidents;
    }

    static List<BannerModSettlementResidentRecord> applyResidentJobTargetSelectionStates(List<BannerModSettlementResidentRecord> residents,
                                                                                        BannerModSettlementMarketState marketState) {
        if (residents.isEmpty()) {
            return List.of();
        }

        List<BannerModSettlementResidentRecord> updatedResidents = new ArrayList<>(residents.size());
        for (BannerModSettlementResidentRecord resident : residents) {
            BannerModSettlementResidentJobTargetSelectionState jobTargetSelectionState = BannerModSettlementResidentJobTargetSelectionState.defaultFor(
                    resident.residentUuid(),
                    resident.jobDefinition(),
                    resident.serviceContract(),
                    marketState
            );
            updatedResidents.add(new BannerModSettlementResidentRecord(
                    resident.residentUuid(),
                    resident.role(),
                    resident.scheduleSeed(),
                    resident.scheduleWindowSeed(),
                    resident.runtimeRoleState(),
                    resident.serviceContract(),
                    resident.jobDefinition(),
                    jobTargetSelectionState,
                    resident.residentMode(),
                    resident.ownerUuid(),
                    resident.teamId(),
                    resident.boundWorkAreaUuid(),
                    resident.assignmentState(),
                    resident.roleProfile(),
                    resident.schedulePolicy()
            ));
        }
        return updatedResidents;
    }

    static List<BannerModSettlementBuildingRecord> collectBuildings(ServerLevel level,
                                                                     RecruitsClaim claim) {
        List<BannerModSettlementBuildingRecord> buildings = new ArrayList<>();
        List<AbstractWorkAreaEntity> workAreas = collectWorkAreas(level, claim, AbstractWorkAreaEntity.class);
        SettlementRecord settlementRecord = settlementRecordForClaim(level, claim);
        List<ValidatedBuildingRecord> validatedBuildings = collectValidatedBuildings(level, settlementRecord);
        Map<UUID, UUID> canonicalBindings = buildCanonicalWorkAreaBindings(validatedBuildings, workAreas);
        Set<UUID> mergedLiveAreas = new LinkedHashSet<>();

        for (ValidatedBuildingRecord record : validatedBuildings) {
            List<AbstractWorkAreaEntity> overlappingAreas = compatibleOverlappingWorkAreas(record, workAreas);
            if (overlappingAreas.isEmpty()) {
                buildings.add(fromValidatedBuilding(record, claim));
                continue;
            }

            AbstractWorkAreaEntity primaryArea = primaryWorkAreaForValidatedBuilding(record, overlappingAreas);
            if (primaryArea == null) {
                buildings.add(fromValidatedBuilding(record, claim));
                continue;
            }

            for (AbstractWorkAreaEntity overlappingArea : overlappingAreas) {
                mergedLiveAreas.add(overlappingArea.getUUID());
            }
            UUID canonicalId = canonicalBindings.getOrDefault(primaryArea.getUUID(), primaryArea.getUUID());
            AbstractWorkAreaEntity canonicalArea = canonicalId.equals(primaryArea.getUUID())
                    ? primaryArea
                    : overlappingAreas.stream()
                            .filter(area -> canonicalId.equals(area.getUUID()))
                            .findFirst()
                            .orElse(primaryArea);
            buildings.add(mergeValidatedBuildingIntoLiveRecord(record, fromLiveWorkArea(canonicalArea)));
        }

        for (AbstractWorkAreaEntity workArea : workAreas) {
            if (!mergedLiveAreas.contains(workArea.getUUID())) {
                buildings.add(fromLiveWorkArea(workArea));
            }
        }
        return buildings;
    }

    static BannerModSettlementBuildingRecord mergeValidatedBuildingIntoLiveRecord(ValidatedBuildingRecord record,
                                                                                  BannerModSettlementBuildingRecord liveRecord) {
        BannerModSettlementBuildingRecord validatedRecord = fromValidatedBuildingFields(
                liveRecord.buildingUuid(),
                record.type(),
                liveRecord.originPos(),
                record.capacity(),
                liveRecord.ownerUuid()
        );
        return new BannerModSettlementBuildingRecord(
                liveRecord.buildingUuid(),
                liveRecord.buildingTypeId(),
                liveRecord.originPos(),
                liveRecord.ownerUuid(),
                liveRecord.teamId(),
                validatedRecord.residentCapacity(),
                validatedRecord.workplaceSlots(),
                0,
                List.of(),
                liveRecord.stockpileBuilding(),
                liveRecord.stockpileContainerCount(),
                liveRecord.stockpileSlotCapacity(),
                liveRecord.stockpileRouteAuthored(),
                liveRecord.stockpilePortEntrypoint(),
                liveRecord.stockpileTypeIds(),
                validatedRecord.buildingCategory(),
                validatedRecord.buildingProfileSeed()
        );
    }

    static BannerModSettlementBuildingRecord fromValidatedBuilding(ValidatedBuildingRecord record,
                                                                   RecruitsClaim claim) {
        return fromValidatedBuildingFields(
                record.buildingId(),
                record.type(),
                record.anchorPos(),
                record.capacity(),
                claim == null || claim.getPlayerInfo() == null ? null : claim.getPlayerInfo().getUUID()
        );
    }

    static BannerModSettlementBuildingRecord fromValidatedBuildingFields(UUID buildingId,
                                                                          BuildingType type,
                                                                          BlockPos anchorPos,
                                                                          int rawCapacity,
                                                                          @Nullable UUID ownerUuid) {
        int capacity = Math.max(1, rawCapacity);
        int residentCapacity = switch (type) {
            case HOUSE, STARTER_FORT -> capacity;
            default -> 0;
        };
        int workplaceSlots = switch (type) {
            case FARM, MINE, LUMBER_CAMP, SMITHY, ARCHITECT_WORKSHOP, BARRACKS -> Math.max(1, PrefabAutoStaffingRuntime.vacancySlotsForManualBuilding(type));
            default -> 0;
        };
        boolean stockpileBuilding = type == BuildingType.STORAGE;
        int stockpileContainers = stockpileBuilding ? Math.max(1, capacity) : 0;
        int stockpileSlots = stockpileBuilding ? Math.max(27, capacity * 27) : 0;
        BannerModSettlementBuildingProfileSeed profileSeed = profileSeedForValidatedBuilding(type);
        return new BannerModSettlementBuildingRecord(
                buildingId,
                "bannermod:validated_" + type.name().toLowerCase(Locale.ROOT),
                anchorPos,
                ownerUuid,
                null,
                residentCapacity,
                workplaceSlots,
                0,
                List.of(),
                stockpileBuilding,
                stockpileContainers,
                stockpileSlots,
                false,
                false,
                stockpileBuilding ? List.of("settlement") : List.of(),
                profileSeed.category(),
                profileSeed
        );
    }

    private static boolean isValidSnapshotBuilding(ServerLevel level, ValidatedBuildingRecord record) {
        return record != null
                && record.state() == BuildingValidationState.VALID
                && record.dimension().equals(level.dimension());
    }

    static boolean validatedBuildingBelongsToSettlement(@Nullable SettlementRecord settlementRecord,
                                                        @Nullable ValidatedBuildingRecord record) {
        return settlementRecord != null
                && record != null
                && settlementRecord.settlementId().equals(record.settlementId());
    }

    private static boolean duplicatesLiveWorkArea(ValidatedBuildingRecord record, List<AbstractWorkAreaEntity> workAreas) {
        for (AbstractWorkAreaEntity workArea : workAreas) {
            if (workArea.getOriginPos().equals(record.anchorPos()) || workArea.getBoundingBox().intersects(record.bounds())) {
                return true;
            }
        }
        return false;
    }

    static List<ValidatedBuildingRecord> collectValidatedBuildings(ServerLevel level,
                                                                   @Nullable SettlementRecord settlementRecord) {
        if (level == null || settlementRecord == null) {
            return List.of();
        }
        List<ValidatedBuildingRecord> records = new ArrayList<>();
        for (ValidatedBuildingRecord record : ValidatedBuildingRegistryData.get(level).allRecords()) {
            if (validatedBuildingBelongsToSettlement(settlementRecord, record) && isValidSnapshotBuilding(level, record)) {
                records.add(record);
            }
        }
        return records;
    }

    static SettlementRecord settlementRecordForClaim(ServerLevel level, RecruitsClaim claim) {
        if (level == null || claim == null) {
            return null;
        }
        return SettlementRegistryData.get(level).getSettlementByClaimId(claim.getUUID());
    }

    private static BannerModSettlementBuildingRecord fromLiveWorkArea(AbstractWorkAreaEntity workArea) {
        StockpileSeed stockpileSeed = resolveStockpileSeed(workArea);
        BannerModSettlementBuildingProfileSeed profileSeed = BannerModSettlementBuildingProfileSeed.fromWorkArea(workArea);
        return new BannerModSettlementBuildingRecord(
                workArea.getUUID(),
                resolveBuildingTypeId(workArea),
                workArea.getOriginPos(),
                workArea.getPlayerUUID(),
                workArea.getTeamStringID(),
                0,
                1,
                0,
                List.of(),
                stockpileSeed.stockpileBuilding(),
                stockpileSeed.containerCount(),
                stockpileSeed.slotCapacity(),
                stockpileSeed.routeAuthored(),
                stockpileSeed.portEntrypoint(),
                stockpileSeed.typeIds(),
                profileSeed.category(),
                profileSeed
        );
    }

    public static Map<UUID, UUID> buildCanonicalWorkAreaBindings(Collection<ValidatedBuildingRecord> validatedBuildings,
                                                                 List<AbstractWorkAreaEntity> workAreas) {
        Map<UUID, UUID> canonicalBindings = new HashMap<>();
        for (ValidatedBuildingRecord record : validatedBuildings) {
            List<AbstractWorkAreaEntity> candidates = compatibleOverlappingWorkAreas(record, workAreas);
            AbstractWorkAreaEntity primary = primaryWorkAreaForValidatedBuilding(record, candidates);
            if (primary == null) {
                continue;
            }
            for (AbstractWorkAreaEntity candidate : candidates) {
                canonicalBindings.put(candidate.getUUID(), primary.getUUID());
            }
        }
        return canonicalBindings;
    }

    private static List<AbstractWorkAreaEntity> compatibleOverlappingWorkAreas(ValidatedBuildingRecord record,
                                                                                List<AbstractWorkAreaEntity> workAreas) {
        if (record == null || workAreas.isEmpty()) {
            return List.of();
        }
        List<AbstractWorkAreaEntity> matches = new ArrayList<>();
        for (AbstractWorkAreaEntity workArea : workAreas) {
            if (isCompatibleValidatedWorkArea(record.type(), workArea)
                    && (workArea.getOriginPos().equals(record.anchorPos()) || workArea.getBoundingBox().intersects(record.bounds()))) {
                matches.add(workArea);
            }
        }
        return matches;
    }

    private static AbstractWorkAreaEntity primaryWorkAreaForValidatedBuilding(ValidatedBuildingRecord record,
                                                                               List<AbstractWorkAreaEntity> candidates) {
        if (record == null || candidates.isEmpty()) {
            return null;
        }
        AbstractWorkAreaEntity best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AbstractWorkAreaEntity candidate : candidates) {
            int score = 0;
            if (candidate.getOriginPos().equals(record.anchorPos())) {
                score += 1000;
            }
            score -= (int) Math.min(999, candidate.getOriginPos().distManhattan(record.anchorPos()));
            if (candidate instanceof CropArea cropArea && !cropArea.getSeedStack().isEmpty()) {
                score += 100;
            }
            if (best == null || score > bestScore || (score == bestScore && candidate.getUUID().toString().compareTo(best.getUUID().toString()) < 0)) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isCompatibleValidatedWorkArea(BuildingType type, AbstractWorkAreaEntity workArea) {
        if (type == null || workArea == null) {
            return false;
        }
        return switch (type) {
            case FARM -> workArea instanceof CropArea;
            case MINE -> workArea instanceof MiningArea;
            case LUMBER_CAMP -> workArea instanceof LumberArea;
            case STORAGE -> workArea instanceof StorageArea;
            default -> false;
        };
    }

    private static BannerModSettlementBuildingProfileSeed profileSeedForValidatedBuilding(BuildingType type) {
        if (type == null) {
            return BannerModSettlementBuildingProfileSeed.GENERAL;
        }
        return switch (type) {
            case FARM -> BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION;
            case MINE, LUMBER_CAMP, SMITHY -> BannerModSettlementBuildingProfileSeed.MATERIAL_PRODUCTION;
            case STORAGE -> BannerModSettlementBuildingProfileSeed.STORAGE;
            case ARCHITECT_WORKSHOP -> BannerModSettlementBuildingProfileSeed.CONSTRUCTION;
            default -> BannerModSettlementBuildingProfileSeed.GENERAL;
        };
    }

    static List<BannerModSettlementBuildingRecord> applyAssignedResidents(List<BannerModSettlementBuildingRecord> buildings,
                                                                          List<BannerModSettlementResidentRecord> residents) {
        if (buildings.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<UUID>> assignedResidentsByBuilding = new LinkedHashMap<>();
        for (BannerModSettlementResidentRecord resident : residents) {
            if (resident.role() != BannerModSettlementResidentRole.CONTROLLED_WORKER
                    || resident.assignmentState() != BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                    || resident.boundWorkAreaUuid() == null) {
                continue;
            }
            assignedResidentsByBuilding.computeIfAbsent(resident.boundWorkAreaUuid(), ignored -> new ArrayList<>())
                    .add(resident.residentUuid());
        }

        List<BannerModSettlementBuildingRecord> updatedBuildings = new ArrayList<>(buildings.size());
        for (BannerModSettlementBuildingRecord building : buildings) {
            List<UUID> assignedResidents = assignedResidentsByBuilding.getOrDefault(building.buildingUuid(), List.of());
            updatedBuildings.add(new BannerModSettlementBuildingRecord(
                    building.buildingUuid(),
                    building.buildingTypeId(),
                    building.originPos(),
                    building.ownerUuid(),
                    building.teamId(),
                    building.residentCapacity(),
                    building.workplaceSlots(),
                    assignedResidents.size(),
                    assignedResidents,
                    building.stockpileBuilding(),
                    building.stockpileContainerCount(),
                    building.stockpileSlotCapacity(),
                    building.stockpileRouteAuthored(),
                    building.stockpilePortEntrypoint(),
                    building.stockpileTypeIds(),
                    building.buildingCategory(),
                    building.buildingProfileSeed()
            ));
        }
        return updatedBuildings;
    }

    static BannerModSettlementStockpileSummary summarizeStockpiles(List<BannerModSettlementBuildingRecord> buildings) {
        return summarizeStockpiles(buildings, List.of());
    }

    static BannerModSettlementStockpileSummary summarizeStockpiles(List<BannerModSettlementBuildingRecord> buildings,
                                                                   List<BannerModSeaTradeEntrypoint> liveSeaTradeEntrypoints) {
        if (buildings.isEmpty()) {
            return BannerModSettlementStockpileSummary.empty();
        }

        int storageBuildingCount = 0;
        int containerCount = 0;
        int slotCapacity = 0;
        int routedStorageCount = 0;
        int portEntrypointCount = 0;
        Set<String> authoredStorageTypeIds = new LinkedHashSet<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            if (!building.stockpileBuilding()) {
                continue;
            }
            storageBuildingCount++;
            containerCount += Math.max(0, building.stockpileContainerCount());
            slotCapacity += Math.max(0, building.stockpileSlotCapacity());
            if (building.stockpileRouteAuthored()) {
                routedStorageCount++;
            }
            if (building.stockpilePortEntrypoint()) {
                portEntrypointCount++;
            }
            authoredStorageTypeIds.addAll(building.stockpileTypeIds());
        }

        Set<UUID> routedStorageIds = new LinkedHashSet<>();
        Set<UUID> portStorageIds = new LinkedHashSet<>();
        for (BannerModSeaTradeEntrypoint entrypoint : liveSeaTradeEntrypoints) {
            routedStorageIds.add(entrypoint.settlementStorageAreaId());
            portStorageIds.add(entrypoint.portStorageAreaId());
        }

        return new BannerModSettlementStockpileSummary(
                storageBuildingCount,
                containerCount,
                slotCapacity,
                routedStorageIds.isEmpty() ? routedStorageCount : routedStorageIds.size(),
                portStorageIds.isEmpty() ? portEntrypointCount : portStorageIds.size(),
                new ArrayList<>(authoredStorageTypeIds)
        );
    }

    static BannerModSettlementMarketState summarizeMarketState(List<BannerModSettlementMarketRecord> markets) {
        if (markets.isEmpty()) {
            return BannerModSettlementMarketState.empty();
        }

        int openMarketCount = 0;
        int totalStorageSlots = 0;
        int freeStorageSlots = 0;
        for (BannerModSettlementMarketRecord market : markets) {
            if (market.open()) {
                openMarketCount++;
            }
            totalStorageSlots += Math.max(0, market.totalStorageSlots());
            freeStorageSlots += Math.max(0, market.freeStorageSlots());
        }

        return new BannerModSettlementMarketState(markets.size(), openMarketCount, totalStorageSlots, freeStorageSlots, 0, 0, markets, List.of());
    }

    static BannerModSettlementDesiredGoodsSnapshot summarizeDesiredGoods(List<BannerModSettlementBuildingRecord> buildings,
                                                                      BannerModSettlementStockpileSummary stockpileSummary,
                                                                      BannerModSettlementMarketState marketState) {
        return summarizeDesiredGoods(buildings, stockpileSummary, marketState, BannerModSeaTradeSummary.summarise(List.of()));
    }

    static BannerModSettlementDesiredGoodsSnapshot summarizeDesiredGoods(List<BannerModSettlementBuildingRecord> buildings,
                                                                      BannerModSettlementStockpileSummary stockpileSummary,
                                                                      BannerModSettlementMarketState marketState,
                                                                      BannerModSeaTradeSummary.Summary seaTradeSummary) {
        Map<String, Integer> desiredGoods = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            String desiredGoodId = switch (building.buildingProfileSeed()) {
                case FOOD_PRODUCTION -> "food";
                case MATERIAL_PRODUCTION -> "materials";
                case CONSTRUCTION -> "construction_materials";
                case MARKET -> "market_goods";
                default -> "";
            };
            addDesiredGoodDriver(desiredGoods, desiredGoodId, 1);
        }
        for (String storageTypeId : stockpileSummary.authoredStorageTypeIds()) {
            addDesiredGoodDriver(desiredGoods, "storage_type:" + storageTypeId, 1);
        }
        addDesiredGoodDriver(desiredGoods, "market_goods", marketState.marketCount());
        addDesiredGoodDriver(desiredGoods, "trade_stock", marketState.openMarketCount());
        for (BannerModSettlementDesiredGoodSnapshot seaTradeDesiredGood : SettlementSeaTradeAnalyzer.desiredGoods(seaTradeSummary)) {
            addDesiredGoodDriver(desiredGoods, seaTradeDesiredGood.desiredGoodId(), seaTradeDesiredGood.driverCount());
        }

        List<BannerModSettlementDesiredGoodSnapshot> desiredGoodSeeds = new ArrayList<>(desiredGoods.size());
        for (Map.Entry<String, Integer> entry : desiredGoods.entrySet()) {
            desiredGoodSeeds.add(new BannerModSettlementDesiredGoodSnapshot(entry.getKey(), entry.getValue()));
        }
        return new BannerModSettlementDesiredGoodsSnapshot(desiredGoodSeeds);
    }

    static BannerModSettlementTradeRouteHandoffSnapshot summarizeTradeRouteHandoffSnapshot(BannerModSettlementStockpileSummary stockpileSummary,
                                                                                     BannerModSettlementMarketState marketState,
                                                                                     BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
                                                                                     ReservationSignalSeed reservationSignalSeed) {
        return summarizeTradeRouteHandoffSnapshot(stockpileSummary, marketState, desiredGoodsSnapshot, reservationSignalSeed, BannerModSeaTradeSummary.summarise(List.of()));
    }

    static BannerModSettlementTradeRouteHandoffSnapshot summarizeTradeRouteHandoffSnapshot(BannerModSettlementStockpileSummary stockpileSummary,
                                                                                      BannerModSettlementMarketState marketState,
                                                                                      BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
                                                                                      ReservationSignalSeed reservationSignalSeed,
                                                                                      BannerModSeaTradeSummary.Summary seaTradeSummary) {
        return summarizeTradeRouteHandoffSnapshot(stockpileSummary, marketState, desiredGoodsSnapshot, reservationSignalSeed, seaTradeSummary, List.of());
    }

    static BannerModSettlementTradeRouteHandoffSnapshot summarizeTradeRouteHandoffSnapshot(BannerModSettlementStockpileSummary stockpileSummary,
                                                                                      BannerModSettlementMarketState marketState,
                                                                                      BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
                                                                                      ReservationSignalSeed reservationSignalSeed,
                                                                                      BannerModSeaTradeSummary.Summary seaTradeSummary,
                                                                                      List<BannerModSeaTradeExecutionRecord> seaTradeExecutionRecords) {
        return new BannerModSettlementTradeRouteHandoffSnapshot(
                marketState.sellerDispatchCount(),
                marketState.readySellerDispatchCount(),
                stockpileSummary.routedStorageCount(),
                stockpileSummary.portEntrypointCount(),
                reservationSignalSeed.activeReservationCount(),
                reservationSignalSeed.reservedUnitCount(),
                desiredGoodsSnapshot.desiredGoods(),
                marketState.sellerDispatches(),
                SettlementSeaTradeAnalyzer.statusLines(seaTradeSummary, seaTradeExecutionRecords)
        );
    }

    static BannerModSettlementSupplySignalState summarizeSupplySignals(BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
                                                                        BannerModSettlementStockpileSummary stockpileSummary,
                                                                        BannerModSettlementMarketState marketState,
                                                                        List<BannerModSettlementResidentRecord> residents,
                                                                        List<BannerModSettlementBuildingRecord> buildings,
                                                                        ReservationSignalSeed reservationSignalSeed) {
        return summarizeSupplySignals(desiredGoodsSnapshot, stockpileSummary, marketState, residents, buildings, reservationSignalSeed, BannerModSeaTradeSummary.summarise(List.of()));
    }

    static BannerModSettlementSupplySignalState summarizeSupplySignals(BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
                                                                        BannerModSettlementStockpileSummary stockpileSummary,
                                                                        BannerModSettlementMarketState marketState,
                                                                        List<BannerModSettlementResidentRecord> residents,
                                                                        List<BannerModSettlementBuildingRecord> buildings,
                                                                        ReservationSignalSeed reservationSignalSeed,
                                                                        BannerModSeaTradeSummary.Summary seaTradeSummary) {
        if (desiredGoodsSnapshot.desiredGoods().isEmpty()) {
            return BannerModSettlementSupplySignalState.empty();
        }

        Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            buildingsByUuid.put(building.buildingUuid(), building);
        }

        Map<String, Integer> serviceCoverageByGood = new LinkedHashMap<>();
        for (BannerModSettlementResidentRecord resident : residents) {
            BannerModSettlementResidentServiceContract serviceContract = resident.serviceContract();
            if (serviceContract.actorState() != BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE
                    || serviceContract.serviceBuildingUuid() == null) {
                continue;
            }

            BannerModSettlementBuildingRecord serviceBuilding = buildingsByUuid.get(serviceContract.serviceBuildingUuid());
            if (serviceBuilding == null) {
                continue;
            }

            String goodId = desiredGoodIdForProfile(serviceBuilding.buildingProfileSeed());
            if (!goodId.isBlank()) {
                serviceCoverageByGood.merge(goodId, 1, Integer::sum);
            }
        }

        List<BannerModSettlementSupplySignal> signals = new ArrayList<>();
        int shortageSignalCount = 0;
        int shortageUnitCount = 0;
        int reservationHintUnitCount = 0;
        for (BannerModSettlementDesiredGoodSnapshot desiredGood : desiredGoodsSnapshot.desiredGoods()) {
            int coverageUnits = resolveSupplyCoverageUnits(desiredGood.desiredGoodId(), stockpileSummary, marketState, serviceCoverageByGood, seaTradeSummary);
            int shortageUnits = Math.max(0, desiredGood.driverCount() - coverageUnits);
            int reservationHintUnits = reservationSignalSeed.reservationHintUnitsByGood().getOrDefault(desiredGood.desiredGoodId(), 0);
            if (shortageUnits > 0) {
                shortageSignalCount++;
                shortageUnitCount += shortageUnits;
            }
            reservationHintUnitCount += reservationHintUnits;
            signals.add(new BannerModSettlementSupplySignal(
                    desiredGood.desiredGoodId(),
                    desiredGood.driverCount(),
                    coverageUnits,
                    shortageUnits,
                    reservationHintUnits
            ));
        }

        return new BannerModSettlementSupplySignalState(
                signals.size(),
                shortageSignalCount,
                shortageUnitCount,
                reservationHintUnitCount,
                signals
        );
    }

    static BannerModSettlementProjectCandidateSnapshot summarizeProjectCandidate(List<BannerModSettlementBuildingRecord> buildings,
                                                                            BannerModSettlementStockpileSummary stockpileSummary,
                                                                            BannerModSettlementDesiredGoodsSnapshot desiredGoodsSnapshot,
                                                                            BannerModSettlementMarketState marketState,
                                                                            boolean governedSettlement,
                                                                            boolean claimedSettlement) {
        Map<BannerModSettlementBuildingProfileSeed, Integer> profileCounts = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            profileCounts.merge(building.buildingProfileSeed(), 1, Integer::sum);
        }

        Map<String, Integer> desiredGoodsById = new LinkedHashMap<>();
        for (BannerModSettlementDesiredGoodSnapshot desiredGood : desiredGoodsSnapshot.desiredGoods()) {
            desiredGoodsById.merge(desiredGood.desiredGoodId(), desiredGood.driverCount(), Integer::sum);
        }

        int governanceBoost = (governedSettlement ? 1 : 0) + (claimedSettlement ? 1 : 0);
        if (stockpileSummary.storageBuildingCount() <= 0 && (!buildings.isEmpty() || !desiredGoodsById.isEmpty())) {
            return new BannerModSettlementProjectCandidateSnapshot(
                    "storage_foundation",
                    BannerModSettlementBuildingProfileSeed.STORAGE,
                    1 + governanceBoost + Math.min(2, desiredGoodsById.size()),
                    governedSettlement,
                    claimedSettlement,
                    List.of("storage_missing", "goods_pressure", marketState.marketCount() > 0 ? "market_access_present" : "market_access_absent")
            );
        }
        if (marketState.marketCount() <= 0 && desiredGoodsById.getOrDefault("market_goods", 0) > 0) {
            return new BannerModSettlementProjectCandidateSnapshot(
                    "market_foundation",
                    BannerModSettlementBuildingProfileSeed.MARKET,
                    1 + governanceBoost + Math.min(2, desiredGoodsById.getOrDefault("market_goods", 0)),
                    governedSettlement,
                    claimedSettlement,
                    List.of("market_missing", "market_goods_demand", stockpileSummary.slotCapacity() > 0 ? "stockpile_ready" : "stockpile_thin")
            );
        }
        if (marketState.marketCount() > marketState.openMarketCount()) {
            return new BannerModSettlementProjectCandidateSnapshot(
                    "market_recovery",
                    BannerModSettlementBuildingProfileSeed.MARKET,
                    1 + governanceBoost + (marketState.marketCount() - marketState.openMarketCount()),
                    governedSettlement,
                    claimedSettlement,
                    List.of("closed_market_capacity", marketState.readySellerDispatchCount() > 0 ? "seller_ready" : "seller_missing")
            );
        }

        BannerModSettlementProjectCandidateSnapshot foodCandidate = buildProfilePressureCandidate(
                "food_capacity_growth",
                BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION,
                desiredGoodsById.getOrDefault("food", 0),
                profileCounts.getOrDefault(BannerModSettlementBuildingProfileSeed.FOOD_PRODUCTION, 0),
                governedSettlement,
                claimedSettlement,
                governanceBoost,
                List.of("food_demand", stockpileSummary.authoredStorageTypeIds().contains("farmers") ? "storage_type:farmers" : "storage_type:generic")
        );
        if (foodCandidate.priority() > 0) {
            return foodCandidate;
        }

        BannerModSettlementProjectCandidateSnapshot materialCandidate = buildProfilePressureCandidate(
                "material_capacity_growth",
                BannerModSettlementBuildingProfileSeed.MATERIAL_PRODUCTION,
                desiredGoodsById.getOrDefault("materials", 0),
                profileCounts.getOrDefault(BannerModSettlementBuildingProfileSeed.MATERIAL_PRODUCTION, 0),
                governedSettlement,
                claimedSettlement,
                governanceBoost,
                List.of("materials_demand")
        );
        if (materialCandidate.priority() > 0) {
            return materialCandidate;
        }

        BannerModSettlementProjectCandidateSnapshot constructionCandidate = buildProfilePressureCandidate(
                "construction_capacity_growth",
                BannerModSettlementBuildingProfileSeed.CONSTRUCTION,
                desiredGoodsById.getOrDefault("construction_materials", 0),
                profileCounts.getOrDefault(BannerModSettlementBuildingProfileSeed.CONSTRUCTION, 0),
                governedSettlement,
                claimedSettlement,
                governanceBoost,
                List.of("construction_demand")
        );
        if (constructionCandidate.priority() > 0) {
            return constructionCandidate;
        }

        return new BannerModSettlementProjectCandidateSnapshot(
                "none",
                null,
                0,
                governedSettlement,
                claimedSettlement,
                List.of()
        );
    }

    static BannerModSettlementMarketState applySellerDispatchSeed(BannerModSettlementMarketState marketState,
                                                                  List<BannerModSettlementResidentRecord> residents,
                                                                  List<BannerModSettlementBuildingRecord> buildings) {
        if (marketState.markets().isEmpty() || residents.isEmpty() || buildings.isEmpty()) {
            return new BannerModSettlementMarketState(
                    marketState.marketCount(),
                    marketState.openMarketCount(),
                    marketState.totalStorageSlots(),
                    marketState.freeStorageSlots(),
                    0,
                    0,
                    marketState.markets(),
                    List.of()
            );
        }

        Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            buildingsByUuid.put(building.buildingUuid(), building);
        }
        Map<UUID, BannerModSettlementMarketRecord> marketsByUuid = new LinkedHashMap<>();
        for (BannerModSettlementMarketRecord market : marketState.markets()) {
            marketsByUuid.put(market.buildingUuid(), market);
        }

        List<BannerModSettlementSellerDispatchRecord> sellerDispatches = new ArrayList<>();
        int readySellerDispatchCount = 0;
        for (BannerModSettlementResidentRecord resident : residents) {
            BannerModSettlementResidentServiceContract serviceContract = resident.serviceContract();
            if (serviceContract.actorState() != BannerModSettlementServiceActorState.LOCAL_BUILDING_SERVICE
                    || serviceContract.serviceBuildingUuid() == null) {
                continue;
            }

            BannerModSettlementBuildingRecord serviceBuilding = buildingsByUuid.get(serviceContract.serviceBuildingUuid());
            if (serviceBuilding == null || serviceBuilding.buildingProfileSeed() != BannerModSettlementBuildingProfileSeed.MARKET) {
                continue;
            }

            BannerModSettlementMarketRecord market = marketsByUuid.get(serviceBuilding.buildingUuid());
            if (market == null) {
                continue;
            }

            BannerModSettlementSellerDispatchState dispatchState = market.open()
                    ? BannerModSettlementSellerDispatchState.READY
                    : BannerModSettlementSellerDispatchState.MARKET_CLOSED;
            if (dispatchState == BannerModSettlementSellerDispatchState.READY) {
                readySellerDispatchCount++;
            }
            sellerDispatches.add(new BannerModSettlementSellerDispatchRecord(
                    resident.residentUuid(),
                    market.buildingUuid(),
                    market.marketName(),
                    dispatchState
            ));
        }

        return new BannerModSettlementMarketState(
                marketState.marketCount(),
                marketState.openMarketCount(),
                marketState.totalStorageSlots(),
                marketState.freeStorageSlots(),
                sellerDispatches.size(),
                readySellerDispatchCount,
                marketState.markets(),
                sellerDispatches
        );
    }

    static BannerModSettlementMarketState collectMarketState(ServerLevel level,
                                                             RecruitsClaim claim) {
        List<BannerModSettlementMarketRecord> markets = new ArrayList<>();
        for (MarketArea marketArea : collectWorkAreas(level, claim, MarketArea.class)) {
            marketArea.scanContainers();
            markets.add(new BannerModSettlementMarketRecord(
                    marketArea.getUUID(),
                    marketArea.getMarketName(),
                    marketArea.isOpen(),
                    marketArea.getTotalSlots(),
                    marketArea.getFreeSlots()
            ));
        }
        return summarizeMarketState(markets);
    }

    static List<StorageArea> collectStorageAreas(ServerLevel level,
                                                 RecruitsClaim claim) {
        return collectWorkAreas(level, claim, StorageArea.class);
    }

    static <T extends AbstractWorkAreaEntity> List<T> collectWorkAreas(ServerLevel level,
                                                                       RecruitsClaim claim,
                                                                       Class<T> type) {
        WorkAreaIndex index = WorkAreaIndex.instance();
        if (index.sizeFor(level.dimension()) > 0) {
            return index.queryInChunks(level, claim.getClaimedChunks(), type).stream()
                    .filter(entity -> claim.containsChunk(entity.chunkPosition()))
                    .toList();
        }
        RuntimeProfilingCounters.increment("work_area.index.fallback_scans");
        return level.getEntitiesOfClass(type, claimBounds(level, claim), entity -> entity.isAlive() && claim.containsChunk(entity.chunkPosition()));
    }

    static List<BannerModSeaTradeEntrypoint> collectLiveSeaTradeEntrypoints(List<StorageArea> storageAreas) {
        return BannerModLogisticsRuntime.listSeaTradeEntrypoints(storageAreas);
    }

    static List<BannerModLogisticsRoute> collectLocalLogisticsRoutes(List<StorageArea> storageAreas) {
        return storageAreas.stream()
                .map(StorageArea::getAuthoredLogisticsRoute)
                .flatMap(Optional::stream)
                .toList();
    }

    static List<BannerModSeaTradeExecutionRecord> collectLocalSeaTradeExecutions(ServerLevel level, List<StorageArea> storageAreas) {
        Set<UUID> storageAreaIds = storageAreas.stream()
                .map(StorageArea::getUUID)
                .collect(java.util.stream.Collectors.toSet());
        if (storageAreaIds.isEmpty()) {
            return List.of();
        }
        return BannerModSeaTradeExecutionSavedData.get(level).runtime().routes().stream()
                .filter(record -> storageAreaIds.contains(record.sourceStorageAreaId())
                        || storageAreaIds.contains(record.destinationStorageAreaId()))
                .toList();
    }

    private static StockpileSeed resolveStockpileSeed(AbstractWorkAreaEntity workArea) {
        if (!(workArea instanceof StorageArea storageArea)) {
            return StockpileSeed.empty();
        }

        storageArea.scanStorageBlocks();
        int slotCapacity = 0;
        for (var container : storageArea.storageMap.values()) {
            slotCapacity += Math.max(0, container.getContainerSize());
        }
        List<String> typeIds = storageArea.getStorageTypes().stream()
                .map(type -> type.name().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
        return new StockpileSeed(
                true,
                storageArea.storageMap.size(),
                slotCapacity,
                storageArea.getAuthoredLogisticsRoute().isPresent(),
                storageArea.isPortEntrypoint(),
                typeIds
        );
    }

    private static void addDesiredGoodDriver(Map<String, Integer> desiredGoods, String desiredGoodId, int driverCount) {
        if (desiredGoodId == null || desiredGoodId.isBlank() || driverCount <= 0) {
            return;
        }
        desiredGoods.merge(desiredGoodId, driverCount, Integer::sum);
    }

    private static int resolveSupplyCoverageUnits(String goodId,
                                                  BannerModSettlementStockpileSummary stockpileSummary,
                                                  BannerModSettlementMarketState marketState,
                                                  Map<String, Integer> serviceCoverageByGood,
                                                  BannerModSeaTradeSummary.Summary seaTradeSummary) {
        int coverageUnits = serviceCoverageByGood.getOrDefault(goodId, 0);
        if (goodId == null || goodId.isBlank()) {
            return coverageUnits;
        }

        coverageUnits = SettlementSeaTradeAnalyzer.addCoverageUnits(goodId, coverageUnits, seaTradeSummary);
        if (goodId.startsWith("storage_type:")) {
            String storageTypeId = goodId.substring("storage_type:".length());
            if (stockpileSummary.authoredStorageTypeIds().contains(storageTypeId)) {
                coverageUnits++;
            }
            return coverageUnits;
        }

        return switch (goodId) {
            case "market_goods" -> coverageUnits + marketState.readySellerDispatchCount();
            case "trade_stock" -> coverageUnits + marketState.openMarketCount() + stockpileSummary.portEntrypointCount();
            default -> coverageUnits;
        };
    }

    private static String desiredGoodIdForProfile(BannerModSettlementBuildingProfileSeed profileSeed) {
        return switch (profileSeed) {
            case FOOD_PRODUCTION -> "food";
            case MATERIAL_PRODUCTION -> "materials";
            case CONSTRUCTION -> "construction_materials";
            case MARKET -> "market_goods";
            default -> "";
        };
    }

    private static BannerModSettlementProjectCandidateSnapshot buildProfilePressureCandidate(String candidateId,
                                                                                          BannerModSettlementBuildingProfileSeed targetProfileSeed,
                                                                                          int desiredCount,
                                                                                         int currentCount,
                                                                                         boolean governedSettlement,
                                                                                         boolean claimedSettlement,
                                                                                         int governanceBoost,
                                                                                         List<String> driverIds) {
        int pressure = desiredCount - currentCount;
        if (pressure <= 0) {
            return BannerModSettlementProjectCandidateSnapshot.empty();
        }
        return new BannerModSettlementProjectCandidateSnapshot(
                candidateId,
                targetProfileSeed,
                Math.min(5, governanceBoost + pressure),
                governedSettlement,
                claimedSettlement,
                driverIds
        );
    }

    static ReservationSignalSeed summarizeReservationSignalSeed(List<BannerModSettlementBuildingRecord> buildings,
                                                                List<BannerModLogisticsRoute> localRoutes,
                                                                List<BannerModLogisticsReservation> reservations) {
        if (buildings.isEmpty() || localRoutes.isEmpty() || reservations.isEmpty()) {
            return ReservationSignalSeed.empty();
        }

        Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : buildings) {
            buildingsByUuid.put(building.buildingUuid(), building);
        }

        Map<UUID, BannerModLogisticsRoute> routesById = new LinkedHashMap<>();
        for (BannerModLogisticsRoute route : localRoutes) {
            routesById.put(route.routeId(), route);
        }

        Map<String, Integer> reservationHintUnitsByGood = new LinkedHashMap<>();
        int activeReservationCount = 0;
        int reservedUnitCount = 0;
        for (BannerModLogisticsReservation reservation : reservations) {
            BannerModLogisticsRoute route = routesById.get(reservation.routeId());
            if (route == null) {
                continue;
            }

            BannerModSettlementBuildingRecord sourceBuilding = buildingsByUuid.get(route.source().storageAreaId());
            BannerModSettlementBuildingRecord destinationBuilding = buildingsByUuid.get(route.destination().storageAreaId());
            activeReservationCount++;
            reservedUnitCount += reservation.reservedCount();

            Set<String> goodIds = new LinkedHashSet<>();
            collectReservationGoodIds(goodIds, sourceBuilding);
            collectReservationGoodIds(goodIds, destinationBuilding);
            if (isMerchantStockpile(sourceBuilding) || isMerchantStockpile(destinationBuilding)) {
                goodIds.add("market_goods");
            }
            if (isPortEntrypoint(sourceBuilding) || isPortEntrypoint(destinationBuilding)) {
                goodIds.add("trade_stock");
            }

            for (String goodId : goodIds) {
                reservationHintUnitsByGood.merge(goodId, reservation.reservedCount(), Integer::sum);
            }
        }

        return new ReservationSignalSeed(activeReservationCount, reservedUnitCount, reservationHintUnitsByGood);
    }

    private static void collectReservationGoodIds(Set<String> goodIds,
                                                  @Nullable BannerModSettlementBuildingRecord building) {
        if (building == null) {
            return;
        }
        for (String stockpileTypeId : building.stockpileTypeIds()) {
            if (stockpileTypeId != null && !stockpileTypeId.isBlank()) {
                goodIds.add("storage_type:" + stockpileTypeId);
            }
        }
    }

    private static boolean isMerchantStockpile(@Nullable BannerModSettlementBuildingRecord building) {
        return building != null && building.stockpileTypeIds().contains("merchants");
    }

    private static boolean isPortEntrypoint(@Nullable BannerModSettlementBuildingRecord building) {
        return building != null && building.stockpilePortEntrypoint();
    }

    private record StockpileSeed(
            boolean stockpileBuilding,
            int containerCount,
            int slotCapacity,
            boolean routeAuthored,
            boolean portEntrypoint,
            List<String> typeIds
    ) {
        private static StockpileSeed empty() {
            return new StockpileSeed(false, 0, 0, false, false, List.of());
        }
    }

    record ReservationSignalSeed(
            int activeReservationCount,
            int reservedUnitCount,
            Map<String, Integer> reservationHintUnitsByGood
    ) {
        ReservationSignalSeed {
            activeReservationCount = Math.max(0, activeReservationCount);
            reservedUnitCount = Math.max(0, reservedUnitCount);
            reservationHintUnitsByGood = Map.copyOf(reservationHintUnitsByGood == null ? Map.of() : reservationHintUnitsByGood);
        }

        static ReservationSignalSeed empty() {
            return new ReservationSignalSeed(0, 0, Map.of());
        }
    }

    private static String resolveBuildingTypeId(AbstractWorkAreaEntity workArea) {
        ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(workArea.getType());
        return typeKey == null ? workArea.getType().toString() : typeKey.toString();
    }

    static ChunkPos resolveAnchorChunk(RecruitsClaim claim) {
        if (claim.getCenter() != null) {
            return claim.getCenter();
        }
        if (!claim.getClaimedChunks().isEmpty()) {
            return claim.getClaimedChunks().get(0);
        }
        return new ChunkPos(0, 0);
    }

    public static AABB claimBounds(ServerLevel level, RecruitsClaim claim) {
        ChunkPos anchor = resolveAnchorChunk(claim);
        int minChunkX = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.x).min().orElse(anchor.x);
        int maxChunkX = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.x).max().orElse(anchor.x);
        int minChunkZ = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.z).min().orElse(anchor.z);
        int maxChunkZ = claim.getClaimedChunks().stream().mapToInt(chunkPos -> chunkPos.z).max().orElse(anchor.z);
        return new AABB(
                minChunkX * 16.0D,
                level.getMinBuildHeight(),
                minChunkZ * 16.0D,
                (maxChunkX + 1) * 16.0D,
                level.getMaxBuildHeight(),
                (maxChunkZ + 1) * 16.0D
        );
    }
}
