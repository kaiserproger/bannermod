package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime;
import com.talhanation.bannermod.settlement.dispatch.SellerPhase;
import com.talhanation.bannermod.settlement.dispatch.SellerPhaseRecord;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.growth.SettlementGrowthContext;
import com.talhanation.bannermod.settlement.growth.SettlementGrowthManager;
import com.talhanation.bannermod.settlement.growth.PendingProject;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentAdvisor;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentRuntime;
import com.talhanation.bannermod.settlement.household.HomePreference;
import com.talhanation.bannermod.settlement.job.JobExecutionContext;
import com.talhanation.bannermod.settlement.project.SettlementProjectRuntime;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderPublishContext;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
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
        List<PendingProject> growthQueue = SettlementGrowthManager.evaluateGrowthQueue(
                growthContext,
                MAX_GROWTH_QUEUE_SIZE
        );
        // Keep settlement founding/player progression manual: passive claim ticks may bind
        // existing BuildAreas, but must not auto-spawn prefab-backed ones on their own.
        state.projectRuntime.tickClaim(
                null,
                snapshot.claimUuid(),
                growthQueue,
                SettlementProjectRuntime.buildAreaResolver(level),
                gameTime
        );

        assignHomes(state.homeRuntime, snapshot, gameTime);
        state.marketStateSupplier.set(snapshot.marketState());
        tickSellerDispatches(state.sellerRuntime, snapshot.marketState(), gameTime);
        publishBuildingWorkOrders(state, snapshot, level, gameTime);
        state.npcDemandContractService.tickClaim(snapshot, gameTime);

        for (SettlementResidentRecord resident : snapshot.residents()) {
            if (resident == null || resident.residentUuid() == null) {
                continue;
            }
            ResidentGoalContext goalContext = new ResidentGoalContext(resident, snapshot, gameTime);
            state.goalScheduler.tick(goalContext);
            runResidentJobStep(state, goalContext);
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
                                    long gameTime) {
        for (SettlementResidentRecord resident : snapshot.residents()) {
            if (resident == null || resident.residentUuid() == null || homeRuntime.homeFor(resident.residentUuid()).isPresent()) {
                continue;
            }
            BannerModHomeAssignmentAdvisor.pickHomeBuilding(resident.residentUuid(), snapshot, homeRuntime)
                    .ifPresent(homeBuildingUuid -> homeRuntime.assign(
                            resident.residentUuid(),
                            homeBuildingUuid,
                            HomePreference.ASSIGNED,
                            gameTime
                    ));
        }
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

        for (SettlementSellerDispatchRecord seed : marketState.sellerDispatches()) {
            if (seed == null
                    || seed.dispatchState() != SettlementSellerDispatchState.READY
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
