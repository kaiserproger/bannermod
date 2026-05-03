package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.BannerModSettlementBuildingRecord;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRecord;
import com.talhanation.bannermod.settlement.dispatch.SellerResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.DeliverResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.FetchResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.IdleResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal;
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
        NpcSocietyAccess.reconcilePhaseOneState(
                level,
                residentUuid,
                householdId,
                homeBuildingUuid,
                workBuildingUuid,
                resolveDailyPhase(ctx, activeTask),
                resolveIntent(ctx, activeTask),
                resolveAnchor(ctx, activeTask, workBuildingUuid, buildingsByUuid),
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
        if (activeTask != null && GoHomeResidentGoal.ID.equals(activeTask.goalId())) {
            return NpcDailyPhase.RETURNING_HOME;
        }
        if (ctx.isRestPhase() || activeTask != null && RestResidentGoal.ID.equals(activeTask.goalId())) {
            return NpcDailyPhase.REST;
        }
        if (ctx.isActivePhase()) {
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
        if (WorkResidentGoal.ID.equals(goalId)) {
            return NpcIntent.WORK;
        }
        if (SellerResidentGoal.ID.equals(goalId)) {
            return NpcIntent.SELL;
        }
        if (SocialiseResidentGoal.ID.equals(goalId)) {
            return NpcIntent.SOCIALISE;
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
                                               @Nullable UUID workBuildingUuid,
                                               Map<UUID, BannerModSettlementBuildingRecord> buildingsByUuid) {
        NpcIntent intent = resolveIntent(ctx, activeTask);
        if (intent == NpcIntent.GO_HOME || intent == NpcIntent.REST) {
            return NpcAnchorType.HOME;
        }
        if (intent == NpcIntent.SELL) {
            return NpcAnchorType.MARKET;
        }
        if (intent == NpcIntent.WORK || intent == NpcIntent.FETCH || intent == NpcIntent.DELIVER) {
            return anchorForWorkBuilding(workBuildingUuid, buildingsByUuid);
        }
        if (intent == NpcIntent.SOCIALISE) {
            return ctx.settlement() != null && ctx.settlement().marketState().openMarketCount() > 0
                    ? NpcAnchorType.MARKET
                    : NpcAnchorType.STREET;
        }
        if (intent == NpcIntent.LEAVE_HOME || intent == NpcIntent.IDLE) {
            return NpcAnchorType.STREET;
        }
        return NpcAnchorType.NONE;
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
