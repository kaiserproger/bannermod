package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.society.NpcHousingProjectPlanner;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcLivelihoodProjectPlanner;
import com.talhanation.bannermod.society.NpcSocietyAnchorGoal;
import com.talhanation.bannermod.society.NpcSocietyNeedRuntime;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime;
import com.talhanation.bannermod.settlement.dispatch.SellerPhase;
import com.talhanation.bannermod.settlement.dispatch.SellerPhaseRecord;
import com.talhanation.bannermod.settlement.goal.ResidentStopReason;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.society.NpcSocietyAccess;
import com.talhanation.bannermod.society.NpcSocietyPhaseOneRuntime;
import com.talhanation.bannermod.society.NpcSocietyProfile;
import com.talhanation.bannermod.settlement.growth.SettlementGrowthContext;
import com.talhanation.bannermod.settlement.growth.SettlementGrowthManager;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentAdvisor;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentRuntime;
import com.talhanation.bannermod.settlement.household.HomePreference;
import com.talhanation.bannermod.settlement.job.JobExecutionContext;
import com.talhanation.bannermod.settlement.project.SettlementProjectRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublishContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class SettlementClaimTickService {

    private static final int MAX_GROWTH_QUEUE_SIZE = 3;

    private SettlementClaimTickService() {
    }

    static void tickSnapshot(SettlementOrchestrator.LevelRuntimeState state,
                             SettlementSnapshot snapshot,
                             @Nullable BannerModGovernorSnapshot governorSnapshot,
                             @Nullable ServerLevel level,
                             long gameTime) {
        if (state == null || snapshot == null) {
            return;
        }

        SettlementGrowthContext growthContext = SettlementGrowthContext.fromSnapshot(
                snapshot,
                governorSnapshot,
                gameTime
        );
        assignHomes(state.homeRuntime, snapshot, level, gameTime);
        Map<UUID, SettlementBuildingRecord> buildingsByUuid = indexBuildings(snapshot);
        List<PendingProject> growthQueue = SettlementGrowthManager.evaluateGrowthQueue(
                growthContext,
                MAX_GROWTH_QUEUE_SIZE
        );
        List<PendingProject> citizenHousingProjects = level == null
                ? List.of()
                : NpcHousingProjectPlanner.collectApprovedHouseProjects(level, snapshot, state.homeRuntime, gameTime);
        List<PendingProject> approvedLivelihoodProjects = level == null
                ? List.of()
                : NpcLivelihoodProjectPlanner.collectApprovedProjects(level, snapshot, gameTime);
        List<PendingProject> combinedGrowthQueue = new java.util.ArrayList<>(growthQueue);
        combinedGrowthQueue.addAll(citizenHousingProjects);
        combinedGrowthQueue.addAll(approvedLivelihoodProjects);
        // Keep settlement founding/player progression manual: passive claim ticks may bind
        // existing BuildAreas, but must not auto-spawn prefab-backed ones on their own.
        state.projectRuntime.tickClaim(
                null,
                snapshot.claimUuid(),
                combinedGrowthQueue,
                SettlementProjectRuntime.buildAreaResolver(level),
                gameTime
        );

        state.marketStateSupplier.set(snapshot.marketState());
        tickSellerDispatches(state.sellerRuntime, snapshot.marketState(), gameTime);
        publishBuildingWorkOrders(state, snapshot, level, gameTime);

        for (SettlementResidentRecord resident : snapshot.residents()) {
            if (resident == null || resident.residentUuid() == null) {
                continue;
            }
            Entity residentEntity = level == null ? null : level.getEntity(resident.residentUuid());
            ResidentTask previousTask = state.goalScheduler.currentTask(resident.residentUuid())
                    .filter(task -> !task.isDone())
                    .orElse(null);
            NpcSocietyProfile profile = preScheduleSocietyTick(level, state.homeRuntime, resident, gameTime, residentEntity, previousTask);
            long worldDayTime = level == null ? gameTime : level.getDayTime();
            ResidentGoalContext goalContext = buildGoalContext(resident, snapshot, gameTime, worldDayTime, profile,
                    residentEntity == null ? null : residentEntity.position());
            state.goalScheduler.tick(goalContext);
            applyRouteInvalidationIfNeeded(state, goalContext);
            runResidentJobStep(state, goalContext);
            syncResidentSocietyProfile(state, goalContext, level, buildingsByUuid);
        }
    }

    static void applyRouteInvalidationIfNeeded(SettlementOrchestrator.LevelRuntimeState state,
                                               ResidentGoalContext goalContext) {
        if (state == null || goalContext == null) {
            return;
        }
        ResidentTask activeTask = state.goalScheduler.currentTask(goalContext.residentId()).orElse(null);
        if (activeTask == null || activeTask.isDone()) {
            return;
        }
        NpcIntent currentIntent = NpcSocietyPhaseOneRuntime.publishedIntentForGoal(activeTask.goalId());
        if (currentIntent == null || currentIntent == NpcIntent.UNSPECIFIED) {
            return;
        }
        if (NpcSocietyAnchorGoal.consumeRouteInvalidation(goalContext.residentId(), currentIntent, goalContext.gameTime())) {
            state.goalScheduler.forceStop(goalContext.residentId(), ResidentStopReason.CONTEXT_INVALID);
        }
    }

    private static void publishBuildingWorkOrders(SettlementOrchestrator.LevelRuntimeState state,
                                                  SettlementSnapshot snapshot,
                                                  @Nullable ServerLevel level,
                                                  long gameTime) {
        if (state.publisherRegistry.size() == 0) {
            return;
        }
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (building == null || building.buildingUuid() == null) {
                continue;
            }
            SettlementWorkOrderPublishContext ctx = new SettlementWorkOrderPublishContext(
                    state.workOrderRuntime,
                    snapshot.claimUuid(),
                    building,
                    snapshot,
                    level,
                    gameTime
            );
            state.publisherRegistry.publishAll(ctx);
        }
    }

    private static void assignHomes(BannerModHomeAssignmentRuntime homeRuntime,
                                    SettlementSnapshot snapshot,
                                    @Nullable ServerLevel level,
                                    long gameTime) {
        if (level != null) {
            assignReservedHomes(homeRuntime, snapshot, level, gameTime);
        }
        java.util.Set<UUID> prioritizedResidents = level == null
                ? java.util.Set.of()
                : NpcHousingProjectPlanner.approvedRequesterIdsForClaim(level, snapshot.claimUuid());
        Map<UUID, SettlementBuildingRecord> buildingsByUuid = indexBuildings(snapshot);
        List<SettlementResidentRecord> orderedResidents;
        if (prioritizedResidents.isEmpty()) {
            orderedResidents = snapshot.residents();
        } else {
            orderedResidents = new java.util.ArrayList<>(snapshot.residents());
            orderedResidents.sort((left, right) -> {
                boolean leftPriority = left != null && left.residentUuid() != null && prioritizedResidents.contains(left.residentUuid());
                boolean rightPriority = right != null && right.residentUuid() != null && prioritizedResidents.contains(right.residentUuid());
                return Boolean.compare(rightPriority, leftPriority);
            });
        }
        for (SettlementResidentRecord resident : orderedResidents) {
            if (resident == null || resident.residentUuid() == null) {
                continue;
            }
            UUID residentUuid = resident.residentUuid();
            Optional<UUID> homeBuildingUuid = homeRuntime.homeFor(residentUuid).map(home -> home.homeBuildingUuid());
            if (homeBuildingUuid.isEmpty()) {
                homeBuildingUuid = BannerModHomeAssignmentAdvisor.pickHomeBuilding(residentUuid, snapshot, homeRuntime);
                homeBuildingUuid.ifPresent(homeUuid -> homeRuntime.assign(
                        residentUuid,
                        homeUuid,
                        HomePreference.ASSIGNED,
                        gameTime
                ));
            }
            if (level != null) {
                NpcSocietyProfile profile = NpcSocietyAccess.ensureResident(level, residentUuid, gameTime);
                if (homeBuildingUuid.isPresent()) {
                    com.talhanation.bannermod.society.NpcHousingRequestAccess.markFulfilled(level, residentUuid, gameTime);
                }
                UUID householdId = reconcileHouseholdMetadataIfNeeded(
                        level,
                        residentUuid,
                        homeBuildingUuid.orElse(null),
                        homeBuildingUuid.map(buildingsByUuid::get)
                                .map(SettlementBuildingRecord::residentCapacity)
                                .orElse(0),
                        profile,
                        gameTime
                );
                NpcSocietyAccess.reconcilePhaseOneState(
                        level,
                        residentUuid,
                        householdId,
                        homeBuildingUuid.orElse(null),
                        resident.effectiveWorkBuildingUuid(),
                        profile.dailyPhase(),
                        profile.currentIntent(),
                        profile.currentAnchor(),
                        profile.decisionSnapshot(),
                        gameTime
                );
            }
        }
    }

    private static @Nullable UUID reconcileHouseholdMetadataIfNeeded(ServerLevel level,
                                                                     UUID residentUuid,
                                                                     @Nullable UUID homeBuildingUuid,
                                                                     int residentCapacity,
                                                                     NpcSocietyProfile profile,
                                                                     long gameTime) {
        if (level == null || residentUuid == null || profile == null) {
            return profile == null ? null : profile.householdId();
        }
        UUID householdIdFromRuntime = com.talhanation.bannermod.society.NpcHouseholdAccess.householdForResident(level, residentUuid)
                .map(com.talhanation.bannermod.society.NpcHouseholdRecord::householdId)
                .orElse(null);
        UUID previousHouseholdId = householdIdFromRuntime == null ? profile.householdId() : householdIdFromRuntime;
        UUID previousHomeBuildingUuid = profile.homeBuildingUuid();
        boolean needsReconcile = householdIdFromRuntime == null || !Objects.equals(previousHomeBuildingUuid, homeBuildingUuid);
        if (!needsReconcile) {
            return previousHouseholdId;
        }
        UUID nextHouseholdId = com.talhanation.bannermod.society.NpcHouseholdAccess.reconcileResidentHome(
                level,
                residentUuid,
                homeBuildingUuid,
                residentCapacity,
                gameTime
        );
        if (nextHouseholdId != null) {
            com.talhanation.bannermod.society.NpcFamilyAccess.reconcileHousehold(level, nextHouseholdId, gameTime);
        }
        if (previousHouseholdId != null && !previousHouseholdId.equals(nextHouseholdId)) {
            com.talhanation.bannermod.society.NpcFamilyAccess.reconcileHousehold(level, previousHouseholdId, gameTime);
        }
        return nextHouseholdId == null ? previousHouseholdId : nextHouseholdId;
    }

    private static void assignReservedHomes(BannerModHomeAssignmentRuntime homeRuntime,
                                            SettlementSnapshot snapshot,
                                            ServerLevel level,
                                            long gameTime) {
        for (com.talhanation.bannermod.society.NpcHousingRequestRecord request
                : com.talhanation.bannermod.society.NpcHousingRequestSavedData.get(level).runtime().requestsForClaim(snapshot.claimUuid())) {
            if (request == null || request.status() == com.talhanation.bannermod.society.NpcHousingRequestStatus.DENIED) {
                continue;
            }
            com.talhanation.bannermod.society.NpcHouseholdRecord household = com.talhanation.bannermod.society.NpcHouseholdAccess.householdFor(level, request.householdId()).orElse(null);
            if (household == null || household.homeBuildingUuid() != null || household.memberResidentUuids().isEmpty()) {
                continue;
            }
            UUID reservedHome = com.talhanation.bannermod.society.NpcHousingPlotPlanner.findReservedHomeBuilding(snapshot, homeRuntime, request);
            if (reservedHome == null) {
                continue;
            }
            int capacity = snapshot.buildings().stream()
                    .filter(building -> building != null && reservedHome.equals(building.buildingUuid()))
                    .mapToInt(com.talhanation.bannermod.settlement.SettlementBuildingRecord::residentCapacity)
                    .findFirst()
                    .orElse(0);
            if (capacity <= 0) {
                continue;
            }
            int assigned = homeRuntime.assignmentsForBuilding(reservedHome).size();
            for (UUID memberResidentUuid : household.memberResidentUuids()) {
                if (memberResidentUuid == null || assigned >= capacity) {
                    continue;
                }
                if (homeRuntime.homeFor(memberResidentUuid).isPresent()) {
                    continue;
                }
                homeRuntime.assign(memberResidentUuid, reservedHome, HomePreference.SHARED, gameTime);
                assigned++;
            }
        }
    }

    private static NpcSocietyProfile preScheduleSocietyTick(@Nullable ServerLevel level,
                                                            BannerModHomeAssignmentRuntime homeRuntime,
                                                            SettlementResidentRecord resident,
                                                            long gameTime,
                                                            @Nullable Entity residentEntity,
                                                            @Nullable ResidentTask previousTask) {
        if (level == null || resident == null || resident.residentUuid() == null) {
            return null;
        }
        UUID residentUuid = resident.residentUuid();
        NpcSocietyProfile profile = NpcSocietyAccess.ensureResident(level, residentUuid, gameTime);
        UUID homeBuildingUuid = homeRuntime.homeFor(residentUuid).map(home -> home.homeBuildingUuid()).orElse(null);
        ResidentGoalContext previewContext = buildGoalContext(
                resident,
                null,
                gameTime,
                level.getDayTime(),
                profile,
                residentEntity == null ? null : residentEntity.position()
        );
        NpcSocietyProfile updatedProfile = NpcSocietyNeedRuntime.tickNeeds(
                profile,
                homeBuildingUuid,
                previewContext.isActivePhase(),
                previewContext.isRestPhase(),
                previousTask,
                isThreatened(residentEntity),
                resident.role() == SettlementResidentRole.GOVERNOR_RECRUIT,
                gameTime
        );
        NpcSocietyProfile needProfile = NpcSocietyAccess.reconcileNeedState(
                level,
                residentUuid,
                updatedProfile.hungerNeed(),
                updatedProfile.fatigueNeed(),
                updatedProfile.safetyNeed(),
                gameTime
        );
        return needProfile;
    }

    private static boolean isThreatened(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return living.hurtTime > 0 || living instanceof Mob mob && mob.getTarget() != null;
    }

    private static ResidentGoalContext buildGoalContext(@Nullable ServerLevel level,
                                                        SettlementResidentRecord resident,
                                                        @Nullable SettlementSnapshot snapshot,
                                                        long gameTime,
                                                        long worldDayTime,
                                                        @Nullable NpcSocietyProfile profile) {
        if (level == null || resident == null || resident.residentUuid() == null) {
            return new ResidentGoalContext(resident, snapshot, gameTime, worldDayTime, profile);
        }
        Entity residentEntity = level.getEntity(resident.residentUuid());
        return buildGoalContext(resident, snapshot, gameTime, worldDayTime, profile,
                residentEntity == null ? null : residentEntity.position());
    }

    private static ResidentGoalContext buildGoalContext(SettlementResidentRecord resident,
                                                        @Nullable SettlementSnapshot snapshot,
                                                        long gameTime,
                                                        long worldDayTime,
                                                        @Nullable NpcSocietyProfile profile,
                                                        @Nullable net.minecraft.world.phys.Vec3 currentPosition) {
        return new ResidentGoalContext(resident, snapshot, gameTime, worldDayTime, profile, currentPosition);
    }

    private static Map<UUID, SettlementBuildingRecord> indexBuildings(SettlementSnapshot snapshot) {
        Map<UUID, SettlementBuildingRecord> buildingsByUuid = new LinkedHashMap<>();
        for (SettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && building.buildingUuid() != null) {
                buildingsByUuid.put(building.buildingUuid(), building);
            }
        }
        return buildingsByUuid;
    }

    private static void syncResidentSocietyProfile(SettlementOrchestrator.LevelRuntimeState state,
                                                   ResidentGoalContext goalContext,
                                                   @Nullable ServerLevel level,
                                                   Map<UUID, SettlementBuildingRecord> buildingsByUuid) {
        if (state == null || goalContext == null || level == null) {
            return;
        }
        Optional<ResidentTask> activeTask = state.goalScheduler.currentTask(goalContext.residentId())
                .filter(task -> !task.isDone());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(
                level,
                state.homeRuntime,
                goalContext,
                activeTask.orElse(null),
                state.goalScheduler.lastOutcome(goalContext.residentId()).orElse(null),
                buildingsByUuid
        );
    }

    private static void tickSellerDispatches(BannerModSellerDispatchRuntime sellerRuntime,
                                             SettlementMarketState marketState,
                                             long gameTime) {
        Set<UUID> openMarkets = new HashSet<>();
        java.util.Map<UUID, UUID> seededMarketsBySeller = new java.util.LinkedHashMap<>();
        for (SettlementMarketRecord market : marketState.markets()) {
            if (market != null && market.open() && market.buildingUuid() != null) {
                openMarkets.add(market.buildingUuid());
            }
        }
        for (SettlementSellerDispatchRecord seed : marketState.sellerDispatches()) {
            if (seed != null && seed.residentUuid() != null && seed.marketUuid() != null) {
                seededMarketsBySeller.put(seed.residentUuid(), seed.marketUuid());
            }
        }

        for (SellerPhaseRecord dispatch : sellerRuntime.activeDispatches()) {
            if (dispatch != null && dispatch.sellerResidentUuid() != null) {
                UUID seededMarketUuid = seededMarketsBySeller.get(dispatch.sellerResidentUuid());
                if (seededMarketUuid == null || !seededMarketUuid.equals(dispatch.marketRecordUuid())) {
                    sellerRuntime.advance(dispatch.sellerResidentUuid(), SellerPhase.CANCELLED, gameTime);
                    continue;
                }
            }
            if (dispatch != null && dispatch.marketRecordUuid() != null && !openMarkets.contains(dispatch.marketRecordUuid())) {
                sellerRuntime.forceMarketClose(dispatch.marketRecordUuid(), gameTime);
            }
        }

        for (SellerPhaseRecord dispatch : sellerRuntime.activeDispatches()) {
            if (dispatch != null && dispatch.sellerResidentUuid() != null) {
                sellerRuntime.tickPhase(dispatch.sellerResidentUuid(), gameTime);
            }
        }
    }

    private static void runResidentJobStep(SettlementOrchestrator.LevelRuntimeState state,
                                           ResidentGoalContext goalContext) {
        SettlementResidentRecord resident = goalContext.resident();
        if (resident.jobDefinition() == null) {
            return;
        }
        Optional<ResidentTask> task = state.goalScheduler.currentTask(resident.residentUuid());
        if (task.isEmpty() || task.get().isDone() || !WorkResidentGoal.ID.equals(task.get().goalId())) {
            return;
        }
        Long cooldownExpiry = state.jobCooldownExpiries.get(resident.residentUuid());
        if (cooldownExpiry != null && goalContext.gameTime() < cooldownExpiry) {
            return;
        }

        JobExecutionContext context = jobContext(state, resident, goalContext.gameTime());
        state.jobHandlerRegistry.lookup(resident.jobDefinition().handlerSeed())
                .filter(handler -> handler.canHandle(context))
                .ifPresent(handler -> {
                    handler.runOneStep(context);
                    if (handler.cooldownTicks() > 0) {
                        state.jobCooldownExpiries.put(resident.residentUuid(), goalContext.gameTime() + handler.cooldownTicks());
                    }
                });
    }

    private static JobExecutionContext jobContext(SettlementOrchestrator.LevelRuntimeState state,
                                                  SettlementResidentRecord resident,
                                                  long gameTime) {
        return new JobExecutionContext(
                resident,
                gameTime,
                resident.residentUuid(),
                resident.effectiveWorkBuildingUuid(),
                state.workOrderRuntime
        );
    }
}
