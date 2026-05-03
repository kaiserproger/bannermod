package com.talhanation.bannermod.settlement.household;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseTwoIntentScorer;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

/**
 * Fires during the run-up to rest phase (and inside rest phase itself) so
 * residents travel to their assigned home before settling down to sleep.
 *
 * <p>Priority 95 — just above {@link com.talhanation.bannermod.settlement.goal.impl.RestResidentGoal}
 * (90) so the "walk home" phase preempts resting. Only armed if the runtime
 * holds a home binding for the resident.
 */
public final class GoHomeResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/go_home");

    private static final int GO_HOME_PRIORITY = 95;
    private static final int GO_HOME_DURATION_TICKS = 120;
    private static final int GO_HOME_COOLDOWN_TICKS = 400;

    /** Tick-window before the rest phase begins where the goal also arms. */
    private static final int APPROACH_WINDOW_TICKS = 500;

    private final BannerModHomeAssignmentRuntime runtime;

    public GoHomeResidentGoal(BannerModHomeAssignmentRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        this.runtime = runtime;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        if (!isRestOrApproachingRest(ctx)) {
            return 0;
        }
        if (this.runtime.homeFor(ctx.residentId()).isEmpty()) {
            return 0;
        }
        int goHomeBias = GO_HOME_PRIORITY;
        if (ctx.isRestPhase()) {
            goHomeBias += 35;
        } else if (ctx.fatigueNeed() >= 80) {
            goHomeBias += 20;
        }
        return Math.max(goHomeBias, NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.GO_HOME));
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        if (this.runtime.homeFor(ctx.residentId()).isEmpty()) {
            return false;
        }
        return this.computePriority(ctx) > 0;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), GO_HOME_DURATION_TICKS);
    }

    @Override
    public int cooldownTicks() {
        return GO_HOME_COOLDOWN_TICKS;
    }

    private static boolean isRestOrApproachingRest(ResidentGoalContext ctx) {
        if (ctx.hasHome() && ctx.fatigueNeed() >= 80) {
            return true;
        }
        if (ctx.isRestPhase()) {
            return true;
        }
        BannerModSettlementResidentScheduleWindowSeed window = ctx.window();
        int now = ctx.dayTime();
        int restStart = window.restStartTick();
        int approachStart = restStart - APPROACH_WINDOW_TICKS;
        if (approachStart < 0) {
            approachStart = 0;
        }
        return now >= approachStart && now < restStart;
    }
}
