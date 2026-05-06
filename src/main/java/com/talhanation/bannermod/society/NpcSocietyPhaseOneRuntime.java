package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.dispatch.SellerResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.DeliverResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.DefendResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.EatResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.FetchResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.HideResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.IdleResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SeekSuppliesResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SocialiseResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.household.BannerModHomeAssignmentRuntime;
import com.talhanation.bannermod.settlement.household.GoHomeResidentGoal;
import com.talhanation.bannermod.settlement.household.LeaveHomeResidentGoal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

public final class NpcSocietyPhaseOneRuntime {
    private NpcSocietyPhaseOneRuntime() {
    }

    public static void updateResidentProfile(ServerLevel level,
                                             BannerModHomeAssignmentRuntime homeRuntime,
                                             ResidentGoalContext ctx,
                                             @Nullable ResidentTask activeTask,
                                             Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid) {
        if (level == null || homeRuntime == null || ctx == null) {
            return;
        }
        UUID residentUuid = ctx.residentId();
        if (residentUuid == null) {
            return;
        }
        UUID homeBuildingUuid = homeRuntime.homeFor(residentUuid)
                .map(home -> home.homeBuildingUuid())
                .orElse(null);
        UUID workBuildingUuid = resolveWorkBuildingUuid(ctx.resident());
        BannerModSettlementBuildingRecord homeBuilding = homeBuildingUuid == null ? null : buildingsByUuid.get(homeBuildingUuid);
        int residentCapacity = homeBuilding == null ? 0 : homeBuilding.residentCapacity();
        UUID householdId = NpcHouseholdAccess.reconcileResidentHome(level, residentUuid, homeBuildingUuid, residentCapacity, ctx.gameTime());
        NpcFamilyAccess.reconcileFamilyForResident(level, residentUuid, ctx.gameTime());
        NpcDailyPhase dailyPhase = resolveDailyPhase(ctx, activeTask);
        NpcIntent currentIntent = resolveIntent(ctx, activeTask);
        NpcAnchorType currentAnchor = resolveAnchor(ctx, activeTask, homeBuildingUuid, workBuildingUuid, buildingsByUuid);
        String routeReasonTag = resolveRouteReason(ctx, homeBuildingUuid, workBuildingUuid, currentIntent, currentAnchor, buildingsByUuid);
        NpcSocietyDecisionSnapshot decisionSnapshot = NpcSocietyDecisionSnapshot.capture(ctx, activeTask, routeReasonTag);
        NpcSocietyAccess.reconcilePhaseOneState(
                level,
                residentUuid,
                householdId,
                homeBuildingUuid,
                workBuildingUuid,
                dailyPhase,
                currentIntent,
                currentAnchor,
                decisionSnapshot,
                ctx.gameTime()
        );
    }

    private static UUID resolveWorkBuildingUuid(BannerModSettlementResidentRecord resident) {
        if (resident == null) {
            return null;
        }
        if (resident.jobDefinition() != null && resident.jobDefinition().targetBuildingUuid() != null) {
            return resident.jobDefinition().targetBuildingUuid();
        }
        return resident.boundWorkAreaUuid();
    }

    private static NpcDailyPhase resolveDailyPhase(ResidentGoalContext ctx, @Nullable ResidentTask activeTask) {
        if (activeTask != null && LeaveHomeResidentGoal.ID.equals(activeTask.goalId())) {
            return NpcDailyPhase.DEPARTING_HOME;
        }
        if (activeTask != null && GoHomeResidentGoal.ID.equals(activeTask.goalId())) {
            return NpcDailyPhase.RETURNING_HOME;
        }
        if (ctx.isRestPhase() || activeTask != null && RestResidentGoal.ID.equals(activeTask.goalId())) {
            return NpcDailyPhase.REST;
        }
        if (ctx.isActivePhase() || ctx.isLeisurePhase()) {
            return NpcDailyPhase.ACTIVE;
        }
        return NpcDailyPhase.UNSPECIFIED;
    }

    private static NpcIntent resolveIntent(ResidentGoalContext ctx, @Nullable ResidentTask activeTask) {
        if (activeTask == null || activeTask.goalId() == null) {
            return ctx.isRestPhase() ? NpcIntent.REST : NpcIntent.IDLE;
        }
        return intentForGoal(activeTask.goalId());
    }

    public static NpcIntent intentForGoal(@Nullable ResourceLocation goalId) {
        if (goalId == null) {
            return NpcIntent.UNSPECIFIED;
        }
        if (GoHomeResidentGoal.ID.equals(goalId)) {
            return NpcIntent.GO_HOME;
        }
        if (LeaveHomeResidentGoal.ID.equals(goalId)) {
            return NpcIntent.LEAVE_HOME;
        }
        if (RestResidentGoal.ID.equals(goalId)) {
            return NpcIntent.REST;
        }
        if (EatResidentGoal.ID.equals(goalId)) {
            return NpcIntent.EAT;
        }
        if (WorkResidentGoal.ID.equals(goalId)) {
            return NpcIntent.WORK;
        }
        if (SeekSuppliesResidentGoal.ID.equals(goalId)) {
            return NpcIntent.SEEK_SUPPLIES;
        }
        if (SellerResidentGoal.ID.equals(goalId)) {
            return NpcIntent.SELL;
        }
        if (SocialiseResidentGoal.ID.equals(goalId)) {
            return NpcIntent.SOCIALISE;
        }
        if (HideResidentGoal.ID.equals(goalId)) {
            return NpcIntent.HIDE;
        }
        if (DefendResidentGoal.ID.equals(goalId)) {
            return NpcIntent.DEFEND;
        }
        if (FetchResidentGoal.ID.equals(goalId)) {
            return NpcIntent.FETCH;
        }
        if (DeliverResidentGoal.ID.equals(goalId)) {
            return NpcIntent.DELIVER;
        }
        if (IdleResidentGoal.ID.equals(goalId)) {
            return NpcIntent.IDLE;
        }
        return NpcIntent.UNSPECIFIED;
    }

    private static NpcAnchorType resolveAnchor(ResidentGoalContext ctx,
                                               @Nullable ResidentTask activeTask,
                                               @Nullable UUID homeBuildingUuid,
                                               @Nullable UUID workBuildingUuid,
                                               Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid) {
        NpcIntent intent = resolveIntent(ctx, activeTask);
        boolean hasHome = homeBuildingUuid != null || ctx.hasHome();
        if (intent == NpcIntent.GO_HOME) {
            return NpcAnchorType.HOME;
        }
        if (intent == NpcIntent.REST) {
            return hasHome ? NpcAnchorType.HOME : NpcAnchorType.STREET;
        }
        if (intent == NpcIntent.EAT) {
            return hasHome ? NpcAnchorType.HOME : NpcAnchorType.MARKET;
        }
        if (intent == NpcIntent.SELL) {
            return NpcAnchorType.MARKET;
        }
        if (intent == NpcIntent.SEEK_SUPPLIES) {
            return ctx.settlement() != null && ctx.settlement().marketState().openMarketCount() > 0
                    ? NpcAnchorType.MARKET
                    : NpcAnchorType.WORKPLACE;
        }
        if (intent == NpcIntent.WORK || intent == NpcIntent.FETCH || intent == NpcIntent.DELIVER) {
            return anchorForWorkBuilding(workBuildingUuid, buildingsByUuid);
        }
        if (intent == NpcIntent.SOCIALISE) {
            if (hasHome && ctx.hasFamilyTies() && ctx.isLeisurePhase()) {
                return NpcAnchorType.HOME;
            }
            return ctx.settlement() != null && ctx.settlement().marketState().openMarketCount() > 0
                    ? NpcAnchorType.MARKET
                    : NpcAnchorType.STREET;
        }
        if (intent == NpcIntent.LEAVE_HOME || intent == NpcIntent.IDLE) {
            return NpcAnchorType.STREET;
        }
        if (intent == NpcIntent.HIDE) {
            return hasHome ? NpcAnchorType.HOME : NpcAnchorType.STREET;
        }
        if (intent == NpcIntent.DEFEND) {
            return NpcAnchorType.BARRACKS;
        }
        return NpcAnchorType.NONE;
    }

    public static String resolveRouteReason(ResidentGoalContext ctx,
                                            @Nullable UUID homeBuildingUuid,
                                            @Nullable UUID workBuildingUuid,
                                            NpcIntent intent,
                                            NpcAnchorType anchor,
                                            Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid) {
        if (intent == NpcIntent.GO_HOME) {
            if (ctx.isRestPhase() || ctx.isLateDayWindow(1000)) {
                return "SOON_NIGHT_HOMEBOUND";
            }
            if (ctx.safetyNeed() >= 70 || ctx.fearScore() >= 60) {
                return "HOME_AS_SHELTER";
            }
            return "RETURNING_HOME_ROUTE";
        }
        if (intent == NpcIntent.REST) {
            if (ctx.lastPublishedIntent() == NpcIntent.GO_HOME || ctx.currentPublishedIntent() == NpcIntent.GO_HOME) {
                return "SETTLING_AT_HOME_FOR_REST";
            }
            return homeBuildingUuid != null ? "RESTING_AT_HOME" : "RESTING_OFF_STREET";
        }
        if (intent == NpcIntent.LEAVE_HOME) {
            return hasWorkAssignment(ctx.resident()) ? "LEAVING_HOME_FOR_WORK" : "LEAVING_HOME_FOR_DAY";
        }
        if (intent == NpcIntent.WORK) {
            return ctx.recentlyCameFromHome() ? "STARTING_WORKDAY_AFTER_HOME" : "HEADING_TO_WORKPLACE";
        }
        if (intent == NpcIntent.EAT) {
            return homeBuildingUuid != null ? "MEAL_AT_HOME" : "MEAL_AT_MARKET";
        }
        if (intent == NpcIntent.SEEK_SUPPLIES) {
            return ctx.settlement() != null && ctx.settlement().marketState().openMarketCount() > 0
                    ? "MARKET_SUPPLY_RUN"
                    : "STOCKPILE_SUPPLY_RUN";
        }
        if (intent == NpcIntent.SOCIALISE) {
            boolean preferHome = anchor == NpcAnchorType.HOME || homeBuildingUuid != null && ctx.hasFamilyTies() && ctx.isLeisurePhase();
            return NpcSocietySocialSpotSelector.select(ctx.settlement(), homeBuildingUuid, preferHome).routeReasonTag();
        }
        if (intent == NpcIntent.HIDE) {
            return "HIDING_FROM_FEAR";
        }
        if (intent == NpcIntent.DEFEND) {
            return "MOVING_TO_DEFENSE_POST";
        }
        if (intent == NpcIntent.SELL) {
            return "MARKET_DUTY_ROUTE";
        }
        if (intent == NpcIntent.FETCH || intent == NpcIntent.DELIVER) {
            return workBuildingUuid != null && buildingsByUuid.containsKey(workBuildingUuid)
                    ? "WORKFLOW_TRANSFER_ROUTE"
                    : "HEADING_TO_WORKPLACE";
        }
        return "NO_CLEAR_ROUTE";
    }

    private static boolean hasWorkAssignment(BannerModSettlementResidentRecord resident) {
        if (resident == null) {
            return false;
        }
        return resident.assignmentState() == com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                || resident.assignmentState() == com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING;
    }

    private static NpcAnchorType anchorForWorkBuilding(@Nullable UUID workBuildingUuid,
                                                       Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid) {
        if (workBuildingUuid == null) {
            return NpcAnchorType.WORKPLACE;
        }
        BannerModSettlementBuildingRecord building = buildingsByUuid.get(workBuildingUuid);
        if (building == null || building.buildingTypeId() == null || building.buildingTypeId().isBlank()) {
            return NpcAnchorType.WORKPLACE;
        }
        ResourceLocation id = ResourceLocation.tryParse(building.buildingTypeId());
        String path = id == null ? building.buildingTypeId() : id.getPath();
        if (path.contains("market")) {
            return NpcAnchorType.MARKET;
        }
        if (path.contains("barracks")) {
            return NpcAnchorType.BARRACKS;
        }
        return NpcAnchorType.WORKPLACE;
    }
}
