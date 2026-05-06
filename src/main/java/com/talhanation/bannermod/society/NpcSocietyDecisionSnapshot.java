package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentRole;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.dispatch.SellerResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.settlement.goal.impl.DefendResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.DeliverResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.EatResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.FetchResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.HideResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.IdleResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SeekSuppliesResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.SocialiseResidentGoal;
import com.talhanation.bannermod.settlement.goal.impl.WorkResidentGoal;
import com.talhanation.bannermod.settlement.household.GoHomeResidentGoal;
import com.talhanation.bannermod.settlement.household.LeaveHomeResidentGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;

public record NpcSocietyDecisionSnapshot(
        String stateTag,
        @Nullable String currentGoalId,
        String choiceReasonTag,
        String routeReasonTag,
        @Nullable String blockedGoalId,
        String blockedReasonTag,
        String lastIntentTag,
        long currentIntentStartedGameTime
) {
    public static NpcSocietyDecisionSnapshot empty() {
        return new NpcSocietyDecisionSnapshot("IDLE", null, "NO_STARTABLE_GOAL", "NO_CLEAR_ROUTE", null, "NONE", NpcIntent.UNSPECIFIED.name(), 0L);
    }

    public static NpcSocietyDecisionSnapshot capture(@Nullable ResidentGoalContext ctx,
                                                     @Nullable ResidentTask activeTask,
                                                     @Nullable String routeReasonTag) {
        if (ctx == null) {
            return empty();
        }
        BlockedGoal blocked = describeBlockedGoal(ctx, activeTask);
        String stateTag = describeState(activeTask, blocked);
        String currentGoalId = activeTask == null || activeTask.goalId() == null ? null : activeTask.goalId().toString();
        String choiceReasonTag = activeTask == null ? "NO_STARTABLE_GOAL" : describeChoiceReason(ctx, activeTask.goalId());
        NpcIntent currentIntent = activeTask == null || activeTask.goalId() == null
                ? (ctx.isRestPhase() ? NpcIntent.REST : NpcIntent.IDLE)
                : NpcSocietyPhaseOneRuntime.intentForGoal(activeTask.goalId());
        NpcIntent previousIntent = ctx.societyProfile() == null || ctx.societyProfile().currentIntent() == null
                ? NpcIntent.UNSPECIFIED
                : ctx.societyProfile().currentIntent();
        long currentIntentStartedGameTime = ctx.gameTime();
        if (ctx.societyProfile() != null
                && ctx.societyProfile().decisionSnapshot() != null
                && currentIntent == previousIntent
                && currentIntent != NpcIntent.UNSPECIFIED) {
            currentIntentStartedGameTime = Math.max(0L, ctx.societyProfile().decisionSnapshot().currentIntentStartedGameTime());
            if (currentIntentStartedGameTime <= 0L) {
                currentIntentStartedGameTime = ctx.gameTime();
            }
        }
        return new NpcSocietyDecisionSnapshot(
                stateTag,
                currentGoalId,
                choiceReasonTag,
                safeTag(routeReasonTag),
                blocked.goalId,
                blocked.reasonTag,
                previousIntent.name(),
                currentIntentStartedGameTime
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("StateTag", safeTag(this.stateTag));
        if (this.currentGoalId != null && !this.currentGoalId.isBlank()) {
            tag.putString("CurrentGoalId", this.currentGoalId);
        }
        tag.putString("ChoiceReasonTag", safeTag(this.choiceReasonTag));
        tag.putString("RouteReasonTag", safeTag(this.routeReasonTag));
        if (this.blockedGoalId != null && !this.blockedGoalId.isBlank()) {
            tag.putString("BlockedGoalId", this.blockedGoalId);
        }
        tag.putString("BlockedReasonTag", safeTag(this.blockedReasonTag));
        tag.putString("LastIntentTag", safeTag(this.lastIntentTag));
        tag.putLong("CurrentIntentStartedGameTime", Math.max(0L, this.currentIntentStartedGameTime));
        return tag;
    }

    public static NpcSocietyDecisionSnapshot fromTag(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return empty();
        }
        return new NpcSocietyDecisionSnapshot(
                safeTag(tag.getString("StateTag")),
                tag.contains("CurrentGoalId") ? tag.getString("CurrentGoalId") : null,
                safeTag(tag.getString("ChoiceReasonTag")),
                safeTag(tag.contains("RouteReasonTag") ? tag.getString("RouteReasonTag") : "NO_CLEAR_ROUTE"),
                tag.contains("BlockedGoalId") ? tag.getString("BlockedGoalId") : null,
                safeTag(tag.getString("BlockedReasonTag")),
                safeTag(tag.contains("LastIntentTag") ? tag.getString("LastIntentTag") : NpcIntent.UNSPECIFIED.name()),
                Math.max(0L, tag.getLong("CurrentIntentStartedGameTime"))
        );
    }

    public String stateTranslationKey() {
        return "gui.bannermod.society.ai.state." + safeTag(this.stateTag).toLowerCase(Locale.ROOT);
    }

    public String choiceReasonTranslationKey() {
        return "gui.bannermod.society.ai.reason." + safeTag(this.choiceReasonTag).toLowerCase(Locale.ROOT);
    }

    public String blockedReasonTranslationKey() {
        return "gui.bannermod.society.ai.reason." + safeTag(this.blockedReasonTag).toLowerCase(Locale.ROOT);
    }

    public static String goalLabelOrDash(@Nullable String goalId) {
        if (goalId == null || goalId.isBlank()) {
            return "-";
        }
        int slash = goalId.lastIndexOf('/');
        return slash >= 0 && slash + 1 < goalId.length() ? goalId.substring(slash + 1) : goalId;
    }

    private static String describeChoiceReason(ResidentGoalContext ctx, @Nullable ResourceLocation goalId) {
        if (goalId == null) {
            return "NO_STARTABLE_GOAL";
        }
        if (GoHomeResidentGoal.ID.equals(goalId)) {
            if (ctx.isRestPhase()) {
                return "REST_WINDOW";
            }
            if (ctx.fatigueNeed() >= 80) {
                return "FATIGUE_SPIKE";
            }
            return ctx.safetyNeed() >= 70 ? "SEEKING_SHELTER" : "HOMEWARD_PULL";
        }
        if (LeaveHomeResidentGoal.ID.equals(goalId)) {
            return "EARLY_ACTIVE_WINDOW";
        }
        if (RestResidentGoal.ID.equals(goalId)) {
            return ctx.isRestPhase() ? "REST_WINDOW" : "FATIGUE_SPIKE";
        }
        if (EatResidentGoal.ID.equals(goalId)) {
            return ctx.hungerNeed() >= 80 ? "SEVERE_HUNGER" : "HUNGER_PRESSURE";
        }
        if (SeekSuppliesResidentGoal.ID.equals(goalId)) {
            return "NO_HOME_FOOD_RUN";
        }
        if (SocialiseResidentGoal.ID.equals(goalId)) {
            return "SOCIAL_PRESSURE";
        }
        if (HideResidentGoal.ID.equals(goalId)) {
            return "THREAT_AVOIDANCE";
        }
        if (DefendResidentGoal.ID.equals(goalId)) {
            return "THREAT_RESPONSE";
        }
        if (SellerResidentGoal.ID.equals(goalId)) {
            return "READY_MARKET_DISPATCH";
        }
        if (WorkResidentGoal.ID.equals(goalId)) {
            return "ASSIGNED_SHIFT";
        }
        if (FetchResidentGoal.ID.equals(goalId) || DeliverResidentGoal.ID.equals(goalId)) {
            return "WORKFLOW_TRANSFER";
        }
        if (IdleResidentGoal.ID.equals(goalId)) {
            return "NO_HIGHER_PRIORITY_GOAL";
        }
        return "UNKNOWN";
    }

    private static String describeState(@Nullable ResidentTask activeTask, BlockedGoal blocked) {
        if (activeTask == null || activeTask.goalId() == null) {
            return blocked.goalId != null ? "BLOCKED" : "IDLE";
        }
        if (IdleResidentGoal.ID.equals(activeTask.goalId())) {
            return blocked.goalId != null ? "BLOCKED" : "IDLE";
        }
        return "EXECUTING";
    }

    private static BlockedGoal describeBlockedGoal(ResidentGoalContext ctx, @Nullable ResidentTask activeTask) {
        if (ctx.safetyNeed() >= 35 && !ctx.canDefend()) {
            if (activeTask == null || HideResidentGoal.ID.equals(activeTask.goalId())) {
                return new BlockedGoal(DefendResidentGoal.ID.toString(), "ROLE_CANNOT_DEFEND");
            }
        }
        if ((ctx.isRestPhase() || ctx.fatigueNeed() >= 70 || ctx.safetyNeed() >= 70) && !ctx.hasHome()) {
            return new BlockedGoal(GoHomeResidentGoal.ID.toString(), "NO_HOME");
        }
        if (ctx.hungerNeed() >= 35 && !ctx.hasHome() && !hasFoodAccess(ctx)) {
            return new BlockedGoal(EatResidentGoal.ID.toString(), "NO_FOOD_ACCESS");
        }
        if (ctx.resident().role() == BannerModSettlementResidentRole.CONTROLLED_WORKER && ctx.isActivePhase()) {
            if (ctx.fatigueNeed() >= 90) {
                return new BlockedGoal(WorkResidentGoal.ID.toString(), "TOO_FATIGUED_FOR_WORK");
            }
            if (!hasWorkAssignment(ctx)) {
                return new BlockedGoal(WorkResidentGoal.ID.toString(), "NO_WORK_ASSIGNMENT");
            }
        }
        if (ctx.socialNeed() >= 60
                && ctx.isActivePhase()
                && !supportsSocialWindow(ctx)
                && (activeTask == null || !SocialiseResidentGoal.ID.equals(activeTask.goalId()))) {
            return new BlockedGoal(SocialiseResidentGoal.ID.toString(), "ROUTINE_WINDOW_MISMATCH");
        }
        return new BlockedGoal(null, "NONE");
    }

    private static boolean hasFoodAccess(ResidentGoalContext ctx) {
        return ctx.settlement() != null && ctx.settlement().marketState().openMarketCount() > 0;
    }

    private static boolean hasWorkAssignment(ResidentGoalContext ctx) {
        BannerModSettlementResidentAssignmentState assignmentState = ctx.resident().assignmentState();
        return assignmentState == BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                || assignmentState == BannerModSettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING;
    }

    private static boolean supportsSocialWindow(ResidentGoalContext ctx) {
        return ctx.window() == BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY
                || ctx.window() == BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX;
    }

    private static String safeTag(@Nullable String value) {
        return value == null || value.isBlank() ? "UNSPECIFIED" : value;
    }

    private record BlockedGoal(@Nullable String goalId, String reasonTag) {
    }
}
