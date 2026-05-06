package com.talhanation.bannermod.settlement.household;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.settlement.SettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

/**
 * Fires during the first stretch of the active window so residents exit their
 * home and reach the streets / their workplace. Pairs with
 * {@link GoHomeResidentGoal} to bracket the home-binding loop.
 *
 * <p>Priority 80 — below go-home (95) and rest (90) but above the regular
 * Work/Socialise fallbacks (60/50) so residents can't be yanked back into
 * work before they've actually left the house.
 */
public final class LeaveHomeResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/leave_home");

    private static final int LEAVE_HOME_PRIORITY = 80;
    private static final int LEAVE_HOME_DURATION_TICKS = 60;
    private static final int LEAVE_HOME_COOLDOWN_TICKS = 400;

    /** Window after active-phase start where this goal may still fire. */
    private static final int EARLY_ACTIVE_WINDOW_TICKS = 500;

    private final BannerModHomeAssignmentRuntime runtime;

    public LeaveHomeResidentGoal(BannerModHomeAssignmentRuntime runtime) {
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
        if (!isEarlyActive(ctx)) {
            return 0;
        }
        if (this.runtime.homeFor(ctx.residentId()).isEmpty()) {
            return 0;
        }
        int priority = LEAVE_HOME_PRIORITY;
        if (ctx.isReadyToFanOutFromLeaveHome()) {
            priority -= 52;
        } else if (ctx.currentPublishedIntent() == com.talhanation.bannermod.society.NpcIntent.LEAVE_HOME) {
            priority += 8;
        }
        return priority;
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        if (this.runtime.homeFor(ctx.residentId()).isEmpty()) {
            return false;
        }
        if (ctx.fatigueNeed() >= 85) {
            return false;
        }
        return isEarlyActive(ctx);
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), LEAVE_HOME_DURATION_TICKS);
    }

    @Override
    public int cooldownTicks() {
        return LEAVE_HOME_COOLDOWN_TICKS;
    }

    private static boolean isEarlyActive(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return false;
        }
        SettlementResidentScheduleWindowSeed window = ctx.window();
        int now = ctx.dayTime();
        int activeStart = window.activeStartTick();
        int cutoff = activeStart + EARLY_ACTIVE_WINDOW_TICKS;
        return now >= activeStart && now < cutoff;
    }
}
