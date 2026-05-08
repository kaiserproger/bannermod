package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseOneRuntime;
import com.talhanation.bannermod.society.NpcSocietyIntentRules;
import com.talhanation.bannermod.settlement.BannerModSettlementMarketState;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime;
import com.talhanation.bannermod.settlement.dispatch.SellerResidentGoal;
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

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Priority-based goal scheduler for settlement residents. Reads the resident's
 * persisted schedule policy + window seed, picks the highest-priority startable
 * {@link ResidentGoal}, and tracks its {@link ResidentTask} + cooldowns in
 * memory. Strictly additive: this class does not itself mutate any resident or
 * worker entity — it only publishes "what this resident should do right now."
 * Down-stream slices (D/E/F) translate published tasks into real NPC behavior.
 *
 * <p>Dormant by default. Registration via
 * {@link #withDefaultGoals()} gives the stock six-goal set; tests supply their
 * own {@code List<ResidentGoal>} for determinism.
 */
public final class BannerModResidentGoalScheduler {
    private static final int SAME_GOAL_STICKINESS_BONUS = 9;
    private static final int SAME_INTENT_STICKINESS_BONUS = 4;
    private static final int SWITCH_MARGIN = 12;
    private static final int ROUTINE_SWITCH_MARGIN = 18;
    private static final int HOME_LOOP_SWITCH_MARGIN = 24;
    private static final int RECENT_FAILURE_MEMORY_TICKS = 240;
    private static final int FAILURE_BASE_COOLDOWN_TICKS = 80;
    private static final int FAILURE_REPEAT_BONUS_TICKS = 40;
    private static final int FAILURE_PRIORITY_PENALTY = 10;
    private static final int FAILURE_INTENT_PENALTY = 6;
    private static final int CONTEXT_INVALID_EXTRA_BACKOFF_TICKS = 40;
    private static final int CONTEXT_INVALID_EXTRA_PENALTY = 4;
    private static final int RECOVERY_STICKINESS_BONUS = 8;
    private static final int HOUSEHOLD_SOCIAL_STICKINESS_BONUS = 8;
    private static final int MEAL_SUPPLY_RECOVERY_BONUS = 10;
    private static final int RECOVERY_SWITCH_MARGIN = 12;
    private static final int HOUSEHOLD_SOCIAL_SWITCH_MARGIN = 10;
    private static final int SUPPLY_RECOVERY_SWITCH_MARGIN = 10;

    private final List<ResidentGoal> goals;
    private final Map<UUID, ResidentTask> activeTasks = new HashMap<>();
    private final Map<UUID, ResidentTask> lastFinishedTasks = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, Long>> cooldownExpiries = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, Integer>> failureCounts = new HashMap<>();
    private final Map<UUID, ResidentTaskOutcome> recentOutcomes = new HashMap<>();

    public BannerModResidentGoalScheduler(List<ResidentGoal> goals) {
        if (goals == null) {
            throw new IllegalArgumentException("goals must not be null");
        }
        this.goals = List.copyOf(goals);
    }

    /** Default scheduler wired with the six stock stub goals. */
    public static BannerModResidentGoalScheduler withDefaultGoals() {
        return new BannerModResidentGoalScheduler(List.of(
                new DefendResidentGoal(),
                new HideResidentGoal(),
                new IdleResidentGoal(),
                new RestResidentGoal(),
                new EatResidentGoal(),
                new WorkResidentGoal(),
                new SeekSuppliesResidentGoal(),
                new SocialiseResidentGoal(),
                new DeliverResidentGoal(),
                new FetchResidentGoal()
        ));
    }

    /**
     * Default scheduler extended with the current Phase 25 household and seller
     * runtime seams. This keeps those slices additive and composable without
     * forcing live settlement orchestration to land in the same change.
     */
    public static BannerModResidentGoalScheduler withDefaultGoals(
            BannerModHomeAssignmentRuntime homeAssignmentRuntime,
            Supplier<BannerModSettlementMarketState> marketStateSupplier,
            BannerModSellerDispatchRuntime sellerDispatchRuntime
    ) {
        if (homeAssignmentRuntime == null) {
            throw new IllegalArgumentException("homeAssignmentRuntime must not be null");
        }
        if (marketStateSupplier == null) {
            throw new IllegalArgumentException("marketStateSupplier must not be null");
        }
        if (sellerDispatchRuntime == null) {
            throw new IllegalArgumentException("sellerDispatchRuntime must not be null");
        }
        return new BannerModResidentGoalScheduler(List.of(
                new DefendResidentGoal(),
                new HideResidentGoal(),
                new GoHomeResidentGoal(homeAssignmentRuntime),
                new RestResidentGoal(),
                new EatResidentGoal(),
                new LeaveHomeResidentGoal(homeAssignmentRuntime),
                new SellerResidentGoal(marketStateSupplier, sellerDispatchRuntime),
                new WorkResidentGoal(),
                new SeekSuppliesResidentGoal(),
                new SocialiseResidentGoal(),
                new DeliverResidentGoal(),
                new FetchResidentGoal(),
                new IdleResidentGoal()
        ));
    }

    /**
     * Advance one tick for the resident in {@code ctx}. If a task is active,
     * advance it; when it completes, record its cooldown. If no task is
     * active, pick the highest-priority startable goal and start it.
     */
    public void tick(ResidentGoalContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        UUID residentId = ctx.residentId();
        ResidentTask active = this.activeTasks.get(residentId);
        if (active != null && !active.isDone()) {
            active.advance();
            if (active.isDone()) {
                if (this.shouldRefreshTimedOutTask(ctx, active)) {
                    this.activeTasks.put(residentId, new ResidentTask(active.goalId(), ctx.gameTime(), active.maxTicks()));
                    return;
                }
                this.onTaskFinished(residentId, active);
            }
            return;
        }
        if (active != null && active.isDone()) {
            this.onTaskFinished(residentId, active);
        }
        this.startNextGoal(ctx);
    }

    /** Current task for a resident, or empty if nothing scheduled. */
    public Optional<ResidentTask> currentTask(UUID residentId) {
        ResidentTask active = this.activeTasks.get(residentId);
        return Optional.ofNullable(active != null ? active : this.lastFinishedTasks.get(residentId));
    }

    public Optional<ResidentTaskOutcome> lastOutcome(UUID residentId) {
        return Optional.ofNullable(this.recentOutcomes.get(residentId));
    }

    /**
     * Forcibly stop the resident's current task with the given reason, record
     * cooldown, and leave the resident idle until the next {@link #tick}.
     */
    public void forceStop(UUID residentId, ResidentStopReason reason) {
        if (residentId == null || reason == null) {
            return;
        }
        ResidentTask active = this.activeTasks.get(residentId);
        if (active == null || active.isDone()) {
            return;
        }
        active.finish(reason);
        this.onTaskFinished(residentId, active);
    }

    /** Drop all in-memory state. Intended for test isolation and reloads. */
    public void reset() {
        this.activeTasks.clear();
        this.lastFinishedTasks.clear();
        this.cooldownExpiries.clear();
        this.failureCounts.clear();
        this.recentOutcomes.clear();
    }

    /** Read-only view of the registered goals, in registration order. */
    public List<ResidentGoal> goals() {
        return this.goals;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void startNextGoal(ResidentGoalContext ctx) {
        ResidentGoal previousGoal = findPreviousGoal(ctx);
        int previousRawPriority = 0;
        ResidentGoal best = null;
        int bestAdjustedPriority = 0;
        int bestRawPriority = 0;
        for (ResidentGoal goal : this.goals) {
            if (this.isOnCooldown(ctx.residentId(), goal.id(), ctx.gameTime())) {
                continue;
            }
            if (!goal.canStart(ctx)) {
                continue;
            }
            int priority = goal.computePriority(ctx);
            if (priority <= 0) {
                continue;
            }
            if (previousGoal != null && previousGoal.id().equals(goal.id())) {
                previousRawPriority = priority;
            }
            int adjustedPriority = adjustedPriority(ctx, goal.id(), priority);
            if (adjustedPriority > bestAdjustedPriority
                    || (adjustedPriority == bestAdjustedPriority && best != null && idOrderBefore(goal.id(), best.id()))) {
                best = goal;
                bestAdjustedPriority = adjustedPriority;
                bestRawPriority = priority;
            }
        }
        if (best != null
                && previousGoal != null
                && !best.id().equals(previousGoal.id())
                && previousRawPriority > 0
                && bestRawPriority < previousRawPriority + switchMargin(ctx, previousGoal, best)) {
            best = previousGoal;
        }
        if (best == null) {
            this.activeTasks.remove(ctx.residentId());
            return;
        }
        ResidentTask task = best.start(ctx);
        if (task == null) {
            this.activeTasks.remove(ctx.residentId());
            return;
        }
        this.activeTasks.put(ctx.residentId(), task);
    }

    private boolean shouldRefreshTimedOutTask(ResidentGoalContext ctx, ResidentTask active) {
        if (ctx == null || active == null || active.stopReason() != ResidentStopReason.TIMED_OUT) {
            return false;
        }
        ResidentGoal goal = this.findGoal(active.goalId());
        if (goal == null || !goal.canStart(ctx)) {
            return false;
        }
        NpcIntent intent = NpcSocietyPhaseOneRuntime.intentForGoal(active.goalId());
        if (intent == NpcIntent.GO_HOME || intent == NpcIntent.REST || intent == NpcIntent.EAT
                || intent == NpcIntent.SEEK_SUPPLIES || intent == NpcIntent.HIDE) {
            return ctx.shouldRefreshSafeRecoveryIntent();
        }
        if (intent == NpcIntent.WORK) {
            return ctx.shouldRefreshWorkIntent();
        }
        if (intent == NpcIntent.SOCIALISE) {
            return ctx.shouldRefreshHouseholdSocial() || ctx.shouldRefreshRoutineSocialIntent();
        }
        return false;
    }

    @Nullable
    private ResidentGoal findPreviousGoal(ResidentGoalContext ctx) {
        if (ctx == null || ctx.societyProfile() == null || ctx.societyProfile().decisionSnapshot() == null) {
            return null;
        }
        String goalId = ctx.societyProfile().decisionSnapshot().currentGoalId();
        if (goalId == null || goalId.isBlank()) {
            return null;
        }
        return this.findGoal(ResourceLocation.tryParse(goalId));
    }

    private int adjustedPriority(ResidentGoalContext ctx, ResourceLocation goalId, int rawPriority) {
        if (ctx == null || goalId == null || rawPriority <= 0 || ctx.societyProfile() == null) {
            return rawPriority;
        }
        int adjusted = rawPriority;
        String previousGoalId = ctx.societyProfile().decisionSnapshot() == null
                ? null
                : ctx.societyProfile().decisionSnapshot().currentGoalId();
        if (goalId.toString().equals(previousGoalId)) {
            adjusted += SAME_GOAL_STICKINESS_BONUS;
        }
        NpcIntent previousIntent = ctx.societyProfile().currentIntent();
        NpcIntent nextIntent = NpcSocietyPhaseOneRuntime.intentForGoal(goalId);
        if (previousIntent != null && previousIntent == nextIntent && nextIntent != NpcIntent.UNSPECIFIED) {
            adjusted += SAME_INTENT_STICKINESS_BONUS;
            if (ctx.shouldHoldCurrentRecoveryIntent() && NpcSocietyIntentRules.isSafeRecoveryIntent(nextIntent)) {
                adjusted += RECOVERY_STICKINESS_BONUS;
            }
        }
        if (nextIntent == NpcIntent.SOCIALISE && ctx.shouldHoldHouseholdSocialIntent()) {
            adjusted += HOUSEHOLD_SOCIAL_STICKINESS_BONUS;
        }
        if (nextIntent == NpcIntent.SEEK_SUPPLIES && ctx.shouldEscalateMealRecoveryToSupplies()) {
            adjusted += MEAL_SUPPLY_RECOVERY_BONUS;
        }
        ResidentTaskOutcome recentOutcome = this.recentOutcomes.get(ctx.residentId());
        if (recentOutcome != null
                && recentOutcome.isFailure()
                && goalId.equals(recentOutcome.goalId())
                && ctx.gameTime() - recentOutcome.finishedGameTime() <= RECENT_FAILURE_MEMORY_TICKS) {
            adjusted -= scaledFailurePenalty(recentOutcome, FAILURE_PRIORITY_PENALTY);
        }
        if (recentOutcome != null
                && recentOutcome.isFailure()
                && ctx.gameTime() - recentOutcome.finishedGameTime() <= RECENT_FAILURE_MEMORY_TICKS) {
            NpcIntent failedIntent = NpcSocietyPhaseOneRuntime.intentForGoal(recentOutcome.goalId());
            if (failedIntent != NpcIntent.UNSPECIFIED
                    && failedIntent == nextIntent
                    && !goalId.equals(recentOutcome.goalId())) {
                adjusted -= scaledFailurePenalty(recentOutcome, FAILURE_INTENT_PENALTY);
            }
            if (NpcSocietyIntentRules.sharesFailureRetryFamily(failedIntent, nextIntent)
                    && failedIntent != nextIntent
                    && !goalId.equals(recentOutcome.goalId())) {
                adjusted -= scaledFailurePenalty(recentOutcome, FAILURE_INTENT_PENALTY + 2);
            }
            adjusted += recoveryPriorityBonus(ctx, failedIntent, nextIntent);
        }
        return adjusted;
    }

    private static int scaledFailurePenalty(ResidentTaskOutcome recentOutcome, int basePenalty) {
        if (recentOutcome == null || basePenalty <= 0) {
            return 0;
        }
        int perFailure = basePenalty;
        if (recentOutcome.stopReason() == ResidentStopReason.CONTEXT_INVALID) {
            perFailure += CONTEXT_INVALID_EXTRA_PENALTY;
        }
        return Math.max(perFailure, recentOutcome.consecutiveFailureCount() * perFailure);
    }

    private static int recoveryPriorityBonus(ResidentGoalContext ctx, NpcIntent failedIntent, NpcIntent nextIntent) {
        if (ctx == null || failedIntent == NpcIntent.UNSPECIFIED || nextIntent == NpcIntent.UNSPECIFIED) {
            return 0;
        }
        int bonus = 0;
        boolean failedRoutine = failedIntent == NpcIntent.WORK
                || failedIntent == NpcIntent.SELL
                || failedIntent == NpcIntent.FETCH
                || failedIntent == NpcIntent.DELIVER
                || failedIntent == NpcIntent.SOCIALISE
                || failedIntent == NpcIntent.SEEK_SUPPLIES;
        boolean failedDailyLife = failedRoutine || failedIntent == NpcIntent.EAT;
        if (failedRoutine && nextIntent == NpcIntent.GO_HOME && ctx.hasHome()) {
            bonus += ctx.hasFamilyTies() ? 10 : 6;
            if (ctx.hasDependents()) {
                bonus += 4;
            }
        }
        if ((failedDailyLife || failedIntent == NpcIntent.GO_HOME)
                && nextIntent == NpcIntent.REST
                && ctx.hasHome()
                && (ctx.isRestPhase() || ctx.fatigueNeed() >= 55)) {
            bonus += ctx.hasFamilyTies() ? 10 : 6;
        }
        if ((failedIntent == NpcIntent.WORK || failedIntent == NpcIntent.SEEK_SUPPLIES || failedIntent == NpcIntent.SOCIALISE)
                && nextIntent == NpcIntent.EAT
                && ctx.hungerNeed() >= 45) {
            bonus += 8;
        }
        if (failedIntent == NpcIntent.EAT
                && nextIntent == NpcIntent.SEEK_SUPPLIES
                && ctx.hungerNeed() >= 50
                && ctx.hasSupplyAccess()) {
            bonus += ctx.hasHome() ? 16 : 12;
            if (ctx.shouldEscalateMealRecoveryToSupplies()) {
                bonus += 6;
            }
            if (ctx.hasOnlyStockpileFoodAccess()) {
                bonus += 4;
            }
        }
        if (failedDailyLife && nextIntent == NpcIntent.HIDE && (ctx.safetyNeed() >= 45 || ctx.fearScore() >= 45)) {
            bonus += ctx.hasDependents() ? 10 : 6;
        }
        return bonus;
    }

    private static int switchMargin(ResidentGoalContext ctx, @Nullable ResidentGoal previousGoal, @Nullable ResidentGoal nextGoal) {
        NpcIntent previousIntent = previousGoal == null ? NpcIntent.UNSPECIFIED : NpcSocietyPhaseOneRuntime.intentForGoal(previousGoal.id());
        NpcIntent nextIntent = nextGoal == null ? NpcIntent.UNSPECIFIED : NpcSocietyPhaseOneRuntime.intentForGoal(nextGoal.id());
        if (ctx != null && previousIntent == NpcIntent.GO_HOME && nextIntent == NpcIntent.REST && ctx.isReadyToSettleAtHome()) {
            return 0;
        }
        if (ctx != null
                && previousIntent == NpcIntent.LEAVE_HOME
                && ctx.isReadyToFanOutFromLeaveHome()
                && (nextIntent == NpcIntent.WORK
                || nextIntent == NpcIntent.SOCIALISE
                || nextIntent == NpcIntent.SELL
                || nextIntent == NpcIntent.FETCH
                || nextIntent == NpcIntent.DELIVER)) {
            return 2;
        }
        int margin = SWITCH_MARGIN;
        if (NpcSocietyIntentRules.isRestLikeIntent(previousIntent) || previousIntent == NpcIntent.LEAVE_HOME) {
            margin = HOME_LOOP_SWITCH_MARGIN;
        } else if (NpcSocietyIntentRules.isAnchoredRoutineIntent(previousIntent)) {
            margin = ROUTINE_SWITCH_MARGIN;
        }
        if (previousIntent != NpcIntent.UNSPECIFIED && previousIntent == nextIntent) {
            margin += 4;
        }
        if (ctx != null) {
            if (ctx.shouldHoldCurrentRecoveryIntent()
                    && NpcSocietyIntentRules.isSafeRecoveryIntent(previousIntent)
                    && NpcSocietyIntentRules.isRoutineDailyIntent(nextIntent)) {
                margin += RECOVERY_SWITCH_MARGIN;
            }
            if (previousIntent == NpcIntent.SOCIALISE
                    && ctx.shouldHoldHouseholdSocialIntent()
                    && NpcSocietyIntentRules.isWorkFamilyIntent(nextIntent)) {
                margin += HOUSEHOLD_SOCIAL_SWITCH_MARGIN;
            }
            if (previousIntent == NpcIntent.SEEK_SUPPLIES
                    && ctx.shouldEscalateMealRecoveryToSupplies()
                    && (nextIntent == NpcIntent.EAT || NpcSocietyIntentRules.isRoutineDailyIntent(nextIntent))) {
                margin += SUPPLY_RECOVERY_SWITCH_MARGIN;
            }
            long currentAge = ctx.currentIntentAgeTicks();
            if (currentAge > 0L && currentAge < 80L) {
                margin += 6;
            } else if (currentAge >= 220L && margin > 4) {
                margin -= 4;
            }
        }
        return Math.max(0, margin);
    }

    private void onTaskFinished(UUID residentId, ResidentTask task) {
        if (residentId == null || task == null || task.stopReason() == null) {
            return;
        }
        ResidentGoal goal = this.findGoal(task.goalId());
        long finishedAt = task.startGameTime() + Math.max(0, task.elapsedTicks());
        long expiresAt = 0L;
        if (goal != null && goal.cooldownTicks() > 0 && task.stopReason() == ResidentStopReason.COMPLETED) {
            expiresAt = finishedAt + goal.cooldownTicks();
        }
        int failureCount = 0;
        if (isFailure(task.stopReason())) {
            failureCount = this.incrementFailureCount(residentId, task.goalId());
            expiresAt = Math.max(expiresAt, finishedAt + failureBackoffTicks(task.goalId(), failureCount, task.stopReason()));
        } else {
            this.clearFailureCount(residentId, task.goalId());
        }
        if (expiresAt > 0L) {
            this.cooldownExpiries
                    .computeIfAbsent(residentId, k -> new HashMap<>())
                    .put(task.goalId(), expiresAt);
        }
        this.activeTasks.remove(residentId);
        this.lastFinishedTasks.put(residentId, task);
        this.recentOutcomes.put(residentId, new ResidentTaskOutcome(task.goalId(), task.stopReason(), finishedAt, failureCount));
    }

    private int incrementFailureCount(UUID residentId, ResourceLocation goalId) {
        Map<ResourceLocation, Integer> perGoal = this.failureCounts.computeIfAbsent(residentId, k -> new HashMap<>());
        int next = Math.min(4, perGoal.getOrDefault(goalId, 0) + 1);
        perGoal.put(goalId, next);
        return next;
    }

    private void clearFailureCount(UUID residentId, ResourceLocation goalId) {
        Map<ResourceLocation, Integer> perGoal = this.failureCounts.get(residentId);
        if (perGoal == null) {
            return;
        }
        perGoal.remove(goalId);
        if (perGoal.isEmpty()) {
            this.failureCounts.remove(residentId);
        }
    }

    private static boolean isFailure(@Nullable ResidentStopReason reason) {
        return reason == ResidentStopReason.TIMED_OUT || reason == ResidentStopReason.CONTEXT_INVALID;
    }

    private static int failureBackoffTicks(ResourceLocation goalId, int failureCount, @Nullable ResidentStopReason reason) {
        NpcIntent intent = NpcSocietyPhaseOneRuntime.intentForGoal(goalId);
        int bonus = NpcSocietyIntentRules.isAnchoredRoutineIntent(intent) || NpcSocietyIntentRules.isRestLikeIntent(intent) ? 30 : 0;
        if (reason == ResidentStopReason.CONTEXT_INVALID) {
            bonus += CONTEXT_INVALID_EXTRA_BACKOFF_TICKS;
        }
        return FAILURE_BASE_COOLDOWN_TICKS + Math.max(0, failureCount - 1) * FAILURE_REPEAT_BONUS_TICKS + bonus;
    }

    @Nullable
    private ResidentGoal findGoal(ResourceLocation id) {
        for (ResidentGoal goal : this.goals) {
            if (goal.id().equals(id)) {
                return goal;
            }
        }
        return null;
    }

    private boolean isOnCooldown(UUID residentId, ResourceLocation goalId, long gameTime) {
        Map<ResourceLocation, Long> perGoal = this.cooldownExpiries.get(residentId);
        if (perGoal == null) {
            return false;
        }
        Long expiresAt = perGoal.get(goalId);
        if (expiresAt == null) {
            return false;
        }
        if (gameTime >= expiresAt.longValue()) {
            perGoal.remove(goalId);
            return false;
        }
        return true;
    }

    private static boolean idOrderBefore(ResourceLocation a, ResourceLocation b) {
        return a.toString().compareTo(b.toString()) < 0;
    }

    // ------------------------------------------------------------------
    // Test hooks (package-private; production code must not rely on these)
    // ------------------------------------------------------------------

    Map<UUID, ResidentTask> activeTasksForTests() {
        return Collections.unmodifiableMap(this.activeTasks);
    }

    Map<UUID, Map<ResourceLocation, Long>> cooldownsForTests() {
        return Collections.unmodifiableMap(this.cooldownExpiries);
    }

    Map<UUID, ResidentTask> finishedTasksForTests() {
        return Collections.unmodifiableMap(this.lastFinishedTasks);
    }
}
