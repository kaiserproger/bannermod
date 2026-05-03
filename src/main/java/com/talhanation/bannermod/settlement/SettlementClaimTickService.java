package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.society.NpcHousingProjectPlanner;
import com.talhanation.bannermod.society.NpcLivelihoodProjectPlanner;
import com.talhanation.bannermod.society.NpcMemoryAccess;
import com.talhanation.bannermod.society.NpcSocietyNeedRuntime;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime;
import com.talhanation.bannermod.settlement.dispatch.SellerPhase;
import com.talhanation.bannermod.settlement.dispatch.SellerPhaseRecord;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.society.NpcSocietyAccess;
import com.talhanation.bannermod.society.NpcSocietyPhaseOneRuntime;
import com.talhanation.bannermod.society.NpcSocietyProfile;
import com.talhanation.bannermod.settlement.growth.BannerModSettlementGrowthContext;
import com.talhanation.bannermod.settlement.growth.BannerModSettlementGrowthManager;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentAdvisor;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentRuntime;
import com.talhanation.bannermod.settlement.household.HomePreference;
import com.talhanation.bannermod.settlement.job.JobExecutionContext;
import com.talhanation.bannermod.settlement.project.BannerModSettlementProjectRuntime;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class BannerModSettlementClaimTickService {

    private static final int MAX_GROWTH_QUEUE_SIZE = 3;

    private BannerModSettlementClaimTickService() {
    }

    static void tickSnapshot(BannerModSettlementOrchestrator.LevelRuntimeState state,
                             BannerModSettlementSnapshot snapshot,
                             @Nullable BannerModGovernorSnapshot governorSnapshot,
                             @Nullable ServerLevel level,
                             long gameTime) {
        if (state == null || snapshot == null) {
            return;
        }

        BannerModSettlementGrowthContext growthContext = BannerModSettlementGrowthContext.fromSnapshot(
                snapshot,
                governorSnapshot,
                gameTime
        );
        assignHomes(state.homeRuntime, snapshot, level, gameTime);
        Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid = indexBuildings(snapshot);
        List<PendingProject> growthQueue = BannerModSettlementGrowthManager.evaluateGrowthQueue(
                growthContext,
                MAX_GROWTH_QUEUE_SIZE
        );
        List<PendingProject> citizenHousingProjects = level == null
                ? List.of()
                : NpcHousingProjectPlanner.collectApprovedHouseProjects(level, snapshot, state.homeRuntime, gameTime);
        List<PendingProject> livelihoodProjects = level == null
                ? List.of()
                : NpcLivelihoodProjectPlanner.collectApprovedProjects(level, snapshot, gameTime);
        List<PendingProject> combinedGrowthQueue = new java.util.ArrayList<>(growthQueue);
        combinedGrowthQueue.addAll(citizenHousingProjects);
        combinedGrowthQueue.addAll(livelihoodProjects);
        // Keep settlement founding/player progression manual: passive claim ticks may bind
        // existing BuildAreas, but must not auto-spawn prefab-backed ones on their own.
        state.projectRuntime.tickClaim(
                null,
                snapshot.claimUuid(),
                combinedGrowthQueue,
                BannerModSettlementProjectRuntime.buildAreaResolver(level),
                gameTime
        );

        state.marketStateSupplier.set(snapshot.marketState());
        tickSellerDispatches(state.sellerRuntime, snapshot.marketState(), gameTime);
        publishBuildingWorkOrders(state, snapshot, level, gameTime);

        for (BannerModSettlementResidentRecord resident : snapshot.residents()) {
            if (resident == null || resident.residentUuid() == null) {
                continue;
            }
            ResidentTask previousTask = state.goalScheduler.currentTask(resident.residentUuid()).orElse(null);
            NpcSocietyProfile profile = preScheduleSocietyTick(level, state.homeRuntime, resident, gameTime, previousTask);
            long worldDayTime = level == null ? gameTime : level.getDayTime();
            ResidentGoalContext goalContext = new ResidentGoalContext(resident, snapshot, gameTime, worldDayTime, profile);
            state.goalScheduler.tick(goalContext);
            runResidentJobStep(state, goalContext);
            syncResidentSocietyProfile(state, goalContext, level, buildingsByUuid);
        }
    }

    private static void publishBuildingWorkOrders(BannerModSettlementOrchestrator.LevelRuntimeState state,
                                                  BannerModSettlementSnapshot snapshot,
                                                  @Nullable ServerLevel level,
                                                  long gameTime) {
        if (state.publisherRegistry.size() == 0) {
            return;
        }
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
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
                                    BannerModSettlementSnapshot snapshot,
                                    @Nullable ServerLevel level,
                                    long gameTime) {
        java.util.Set<UUID> prioritizedResidents = level == null
                ? java.util.Set.of()
                : NpcHousingProjectPlanner.approvedRequesterIdsForClaim(level, snapshot.claimUuid());
        Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid = indexBuildings(snapshot);
        List<BannerModSettlementResidentRecord> orderedResidents = new java.util.ArrayList<>(snapshot.residents());
        orderedResidents.sort((left, right) -> {
            boolean leftPriority = left != null && left.residentUuid() != null && prioritizedResidents.contains(left.residentUuid());
            boolean rightPriority = right != null && right.residentUuid() != null && prioritizedResidents.contains(right.residentUuid());
            return Boolean.compare(rightPriority, leftPriority);
        });
        for (BannerModSettlementResidentRecord resident : orderedResidents) {
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
                NpcSocietyAccess.ensureResident(level, residentUuid, gameTime);
                if (homeBuildingUuid.isPresent()) {
                    com.talhanation.bannermod.society.NpcHousingRequestAccess.markFulfilled(level, residentUuid, gameTime);
                }
                UUID householdId = com.talhanation.bannermod.society.NpcHouseholdAccess.reconcileResidentHome(
                        level,
                        residentUuid,
                        homeBuildingUuid.orElse(null),
                        homeBuildingUuid.map(buildingsByUuid::get)
                                .map(BannerModSettlementBuildingRecord::residentCapacity)
                                .orElse(0),
                        gameTime
                );
                com.talhanation.bannermod.society.NpcFamilyAccess.reconcileFamilyForResident(level, residentUuid, gameTime);
                NpcSocietyAccess.reconcilePhaseOneState(
                        level,
                        residentUuid,
                        householdId,
                        homeBuildingUuid.orElse(null),
                        resident.boundWorkAreaUuid(),
                        com.talhanation.bannermod.society.NpcDailyPhase.UNSPECIFIED,
                        com.talhanation.bannermod.society.NpcIntent.UNSPECIFIED,
                        com.talhanation.bannermod.society.NpcAnchorType.NONE,
                        gameTime
                );
            }
        }
    }

    private static NpcSocietyProfile preScheduleSocietyTick(@Nullable ServerLevel level,
                                                            BannerModHomeAssignmentRuntime homeRuntime,
                                                            BannerModSettlementResidentRecord resident,
                                                            long gameTime,
                                                            @Nullable ResidentTask previousTask) {
        if (level == null || resident == null || resident.residentUuid() == null) {
            return null;
        }
        UUID residentUuid = resident.residentUuid();
        NpcSocietyProfile profile = NpcSocietyAccess.ensureResident(level, residentUuid, gameTime);
        UUID homeBuildingUuid = homeRuntime.homeFor(residentUuid).map(home -> home.homeBuildingUuid()).orElse(null);
        ResidentGoalContext previewContext = new ResidentGoalContext(resident, null, gameTime, level.getDayTime(), profile);
        Entity residentEntity = level == null ? null : level.getEntity(residentUuid);
        NpcSocietyProfile updatedProfile = NpcSocietyNeedRuntime.tickNeeds(
                profile,
                homeBuildingUuid,
                previewContext.isActivePhase(),
                previewContext.isRestPhase(),
                previousTask,
                isThreatened(residentEntity),
                resident.role() == BannerModSettlementResidentRole.GOVERNOR_RECRUIT,
                gameTime
        );
        NpcSocietyProfile needProfile = NpcSocietyAccess.reconcileNeedState(
                level,
                residentUuid,
                updatedProfile.hungerNeed(),
                updatedProfile.fatigueNeed(),
                updatedProfile.socialNeed(),
                updatedProfile.safetyNeed(),
                gameTime
        );
        return NpcMemoryAccess.tickResidentState(level, needProfile, gameTime);
    }

    private static boolean isThreatened(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return living.hurtTime > 0 || living instanceof Mob mob && mob.getTarget() != null;
    }

    private static Map<UUID, BannerModSettlementBuildingRecord> indexBuildings(BannerModSettlementSnapshot snapshot) {
        Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid = new LinkedHashMap<>();
        for (BannerModSettlementBuildingRecord building : snapshot.buildings()) {
            if (building != null && building.buildingUuid() != null) {
                buildingsByUuid.put(building.buildingUuid(), building);
            }
        }
        return buildingsByUuid;
    }

    private static void syncResidentSocietyProfile(BannerModSettlementOrchestrator.LevelRuntimeState state,
                                                   ResidentGoalContext goalContext,
                                                   @Nullable ServerLevel level,
                                                   Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid) {
        if (state == null || goalContext == null || level == null) {
            return;
        }
        Optional<ResidentTask> activeTask = state.goalScheduler.currentTask(goalContext.residentId())
                .filter(task -> task != null && !task.isDone());
        NpcSocietyPhaseOneRuntime.updateResidentProfile(
                level,
                state.homeRuntime,
                goalContext,
                activeTask.orElse(null),
                buildingsByUuid
        );
    }

    private static void tickSellerDispatches(BannerModSellerDispatchRuntime sellerRuntime,
                                             BannerModSettlementMarketState marketState,
                                             long gameTime) {
        Set<UUID> openMarkets = new HashSet<>();
        java.util.Map<UUID, UUID> seededMarketsBySeller = new java.util.LinkedHashMap<>();
        for (BannerModSettlementMarketRecord market : marketState.markets()) {
            if (market != null && market.open() && market.buildingUuid() != null) {
                openMarkets.add(market.buildingUuid());
            }
        }
        for (BannerModSettlementSellerDispatchRecord seed : marketState.sellerDispatches()) {
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

        for (BannerModSettlementSellerDispatchRecord seed : marketState.sellerDispatches()) {
            if (seed == null
                    || seed.dispatchState() != BannerModSettlementSellerDispatchState.READY
                    || seed.residentUuid() == null
                    || seed.marketUuid() == null
                    || !openMarkets.contains(seed.marketUuid())
                    || sellerRuntime.isActive(seed.residentUuid())) {
                continue;
            }
            try {
                sellerRuntime.beginDispatch(seed.residentUuid(), seed.marketUuid(), gameTime);
            } catch (IllegalStateException ignored) {
                // Another claim tick may have started the dispatch already; keep this seam additive.
            }
        }

        for (SellerPhaseRecord dispatch : sellerRuntime.activeDispatches()) {
            if (dispatch != null && dispatch.sellerResidentUuid() != null) {
                sellerRuntime.tickPhase(dispatch.sellerResidentUuid(), gameTime);
            }
        }
    }

    private static void runResidentJobStep(BannerModSettlementOrchestrator.LevelRuntimeState state,
                                           ResidentGoalContext goalContext) {
        BannerModSettlementResidentRecord resident = goalContext.resident();
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

    private static JobExecutionContext jobContext(BannerModSettlementOrchestrator.LevelRuntimeState state,
                                                  BannerModSettlementResidentRecord resident,
                                                  long gameTime) {
        UUID workplaceUuid = resident.jobDefinition() == null || resident.jobDefinition().targetBuildingUuid() == null
                ? resident.boundWorkAreaUuid()
                : resident.jobDefinition().targetBuildingUuid();
        return new JobExecutionContext(
                resident,
                gameTime,
                resident.residentUuid(),
                workplaceUuid,
                state.workOrderRuntime
        );
    }
}
