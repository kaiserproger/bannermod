package com.talhanation.bannermod.settlement.goal;

import com.talhanation.bannermod.settlement.SettlementMarketState;
import com.talhanation.bannermod.settlement.dispatch.BannerModSellerDispatchRuntime;
import com.talhanation.bannermod.settlement.dispatch.SellerResidentGoal;
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

    private final List<ResidentGoal> goals;
    private final Map<UUID, ResidentTask> activeTasks = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, Long>> cooldownExpiries = new HashMap<>();

    public BannerModResidentGoalScheduler(List<ResidentGoal> goals) {
        if (goals == null) {
            throw new IllegalArgumentException("goals must not be null");
        }
        this.goals = List.copyOf(goals);
    }

    /** Default scheduler wired with the six stock stub goals. */
    public static BannerModResidentGoalScheduler withDefaultGoals() {
        return new BannerModResidentGoalScheduler(List.of(
                new IdleResidentGoal(),
                new RestResidentGoal(),
                new WorkResidentGoal(),
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
                new GoHomeResidentGoal(homeAssignmentRuntime),
                new RestResidentGoal(),
                new LeaveHomeResidentGoal(homeAssignmentRuntime),
                new SellerResidentGoal(marketStateSupplier, sellerDispatchRuntime),
                new WorkResidentGoal(),
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
        return Optional.ofNullable(this.activeTasks.get(residentId));
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
        this.cooldownExpiries.clear();
    }

    /** Read-only view of the registered goals, in registration order. */
    public List<ResidentGoal> goals() {
        return this.goals;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void startNextGoal(ResidentGoalContext ctx) {
        ResidentGoal best = null;
        int bestPriority = 0;
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
            if (priority > bestPriority
                    || (priority == bestPriority && best != null && idOrderBefore(goal.id(), best.id()))) {
                best = goal;
                bestPriority = priority;
            }
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

    private void onTaskFinished(UUID residentId, ResidentTask task) {
        ResidentGoal goal = this.findGoal(task.goalId());
        long expiresAt = 0L;
        if (goal != null && goal.cooldownTicks() > 0 && task.stopReason() == ResidentStopReason.COMPLETED) {
            expiresAt = task.startGameTime() + task.elapsedTicks() + goal.cooldownTicks();
        }
        if (expiresAt > 0L) {
            this.cooldownExpiries
                    .computeIfAbsent(residentId, k -> new HashMap<>())
                    .put(task.goalId(), expiresAt);
        }
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
}
