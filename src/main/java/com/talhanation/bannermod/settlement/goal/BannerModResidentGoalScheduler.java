package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseOneRuntime;
import com.talhanation.bannermod.society.NpcSocietyIntentRules;
import com.talhanation.bannermod.settlement.SettlementMarketState;
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
 * worker entity; it only publishes "what this resident should do right now."
 * Downstream runtime code translates published tasks into real NPC behavior.
 *
 * <p>Dormant by default. Registration via
 * {@link #withDefaultGoals()} gives the stock six-goal set; tests supply their
 * own {@code List<ResidentGoal>} for determinism.
 */
public final class BannerModResidentGoalScheduler {
    private static final int FAILURE_BASE_COOLDOWN_TICKS = 80;
    private static final int CONTEXT_INVALID_EXTRA_BACKOFF_TICKS = 40;
    private static final int GOAL_REEVALUATION_HEARTBEAT_TICKS = 20;

    private final List<ResidentGoal> goals;
    private final Map<ResourceLocation, ResidentGoal> goalsById;
    private final Map<UUID, ResidentTask> activeTasks = new HashMap<>();
    private final Map<UUID, ResidentTask> lastFinishedTasks = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, Long>> cooldownExpiries = new HashMap<>();
    private final Map<UUID, ResidentTaskOutcome> recentOutcomes = new HashMap<>();

    public BannerModResidentGoalScheduler(List<ResidentGoal> goals) {
        if (goals == null) {
            throw new IllegalArgumentException("goals must not be null");
        }
        this.goals = List.copyOf(goals);
        Map<ResourceLocation, ResidentGoal> goalsById = new HashMap<>();
        for (ResidentGoal goal : this.goals) {
            if (goal != null && goal.id() != null) {
                goalsById.put(goal.id(), goal);
            }
        }
        this.goalsById = Map.copyOf(goalsById);
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
                new DeliverResidentGoal(),
                new FetchResidentGoal()
        ));
    }

    /** Default scheduler extended with household and seller runtime seams. */
    public static BannerModResidentGoalScheduler withDefaultGoals(
            BannerModHomeAssignmentRuntime homeAssignmentRuntime,
            Supplier<SettlementMarketState> marketStateSupplier,
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
            GoalSelection alternative = this.shouldEvaluateAlternativeGoal(ctx, active)
                    ? this.selectBestGoal(ctx, active.goalId())
                    : null;
            if (this.shouldPreemptActiveTask(ctx, active, alternative)) {
                active.syncElapsed(ctx.gameTime());
                if (active.finishTimedOutIfExpired()) {
                    this.onTaskFinished(residentId, active);
                    this.startSelection(ctx, alternative);
                    return;
                }
                active.finish(ResidentStopReason.PRE_EMPTED);
                this.onTaskFinished(residentId, active);
                this.startSelection(ctx, alternative);
                return;
            }
            active.advance(ctx.gameTime());
            if (active.isDone()) {
                this.onTaskFinished(residentId, active);
            }
            return;
        }
        if (active != null && active.isDone()) {
            this.onTaskFinished(residentId, active);
        }
        this.startNextGoal(ctx);
    }

    /** Current active task, or the most recent finished task if nothing is active. */
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
        this.startSelection(ctx, this.selectBestGoal(ctx, null));
    }

    private void startSelection(ResidentGoalContext ctx, @Nullable GoalSelection selection) {
        if (selection == null) {
            this.activeTasks.remove(ctx.residentId());
            return;
        }
        ResidentTask task = selection.goal.start(ctx);
        if (task == null) {
            this.activeTasks.remove(ctx.residentId());
            return;
        }
        this.activeTasks.put(ctx.residentId(), task);
    }

    private @Nullable GoalSelection selectBestGoal(ResidentGoalContext ctx, @Nullable ResourceLocation excludedGoalId) {
        ResidentGoal best = null;
        int bestPriority = 0;
        for (ResidentGoal goal : this.goals) {
            if (excludedGoalId != null && excludedGoalId.equals(goal.id())) {
                continue;
            }
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
            if (best == null
                    || priority > bestPriority
                    || (priority == bestPriority && idOrderBefore(goal.id(), best.id()))) {
                best = goal;
                bestPriority = priority;
            }
        }
        return best == null ? null : new GoalSelection(best);
    }

    private boolean shouldPreemptActiveTask(ResidentGoalContext ctx,
                                             ResidentTask activeTask,
                                             @Nullable GoalSelection alternative) {
        if (ctx == null || activeTask == null || activeTask.goalId() == null || alternative == null) {
            return false;
        }
        ResidentGoal activeGoal = this.findGoal(activeTask.goalId());
        if (activeGoal == null) {
            return true;
        }
        int currentPriority = activeGoal.computePriority(ctx);
        if (currentPriority <= 0 || !activeGoal.canStart(ctx)) {
            return true;
        }
        ResourceLocation currentGoalId = activeTask.goalId();
        ResourceLocation nextGoalId = alternative.goal.id();
        if (currentGoalId.equals(nextGoalId)) {
            return false;
        }
        if (IdleResidentGoal.ID.equals(currentGoalId)) {
            return true;
        }
        if (GoHomeResidentGoal.ID.equals(currentGoalId)
                && RestResidentGoal.ID.equals(nextGoalId)
                && ctx.isReadyToSettleAtHome()) {
            return true;
        }
        if (isDangerOverride(nextGoalId)) {
            return !isDangerOverride(currentGoalId);
        }
        return isNightHomeOverride(nextGoalId)
                && !isNightHomeOverride(currentGoalId)
                && (ctx.isRestPhase() || ctx.fatigueNeed() >= 85 || ctx.safetyNeed() >= 70);
    }

    private boolean shouldEvaluateAlternativeGoal(ResidentGoalContext ctx, ResidentTask activeTask) {
        if (ctx == null || activeTask == null || activeTask.goalId() == null) {
            return false;
        }
        if (IdleResidentGoal.ID.equals(activeTask.goalId())) {
            return true;
        }
        if (ctx.safetyNeed() >= 35) {
            return true;
        }
        if (ctx.isRestPhase() && !isNightHomeOverride(activeTask.goalId())) {
            return true;
        }
        long activeAge = Math.max(0L, ctx.gameTime() - activeTask.startGameTime());
        return activeAge <= 0L || activeAge % GOAL_REEVALUATION_HEARTBEAT_TICKS == 0L;
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
        if (isFailure(task.stopReason())) {
            expiresAt = Math.max(expiresAt, finishedAt + failureBackoffTicks(task.stopReason()));
        }
        if (expiresAt > 0L) {
            this.cooldownExpiries
                    .computeIfAbsent(residentId, k -> new HashMap<>())
                    .put(task.goalId(), expiresAt);
        }
        this.activeTasks.remove(residentId);
        this.lastFinishedTasks.put(residentId, task);
        this.recentOutcomes.put(residentId, new ResidentTaskOutcome(task.goalId(), task.stopReason(), finishedAt));
    }

    private static boolean isFailure(@Nullable ResidentStopReason reason) {
        return reason == ResidentStopReason.TIMED_OUT || reason == ResidentStopReason.CONTEXT_INVALID;
    }

    private static int failureBackoffTicks(@Nullable ResidentStopReason reason) {
        return FAILURE_BASE_COOLDOWN_TICKS
                + (reason == ResidentStopReason.CONTEXT_INVALID ? CONTEXT_INVALID_EXTRA_BACKOFF_TICKS : 0);
    }

    @Nullable
    private ResidentGoal findGoal(ResourceLocation id) {
        return id == null ? null : this.goalsById.get(id);
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

    private static boolean isDangerOverride(ResourceLocation goalId) {
        return HideResidentGoal.ID.equals(goalId) || DefendResidentGoal.ID.equals(goalId);
    }

    private static boolean isNightHomeOverride(ResourceLocation goalId) {
        return GoHomeResidentGoal.ID.equals(goalId) || RestResidentGoal.ID.equals(goalId);
    }

    private record GoalSelection(ResidentGoal goal) {
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
