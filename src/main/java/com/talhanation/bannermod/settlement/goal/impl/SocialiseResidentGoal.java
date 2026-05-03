package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentScheduleWindowSeed;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

/** Mid-priority leisure goal active during civic-day windows. */
public final class SocialiseResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/socialise");

    private static final int SOCIALISE_PRIORITY = 30;
    private static final int SOCIALISE_DURATION_TICKS = 120;
    private static final int SOCIALISE_COOLDOWN_TICKS = 400;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return 0;
        }
        if (ctx.window() != BannerModSettlementResidentScheduleWindowSeed.CIVIC_DAY
                && ctx.window() != BannerModSettlementResidentScheduleWindowSeed.DAYLIGHT_FLEX) {
            return 0;
        }
        int priority = SOCIALISE_PRIORITY + ctx.socialNeed() / 3;
        if (ctx.isAdolescent()) {
            priority += 8;
        }
        if (ctx.dayTime() > 9000) {
            priority += 6;
        }
        priority -= ctx.fatigueNeed() / 10;
        return Math.max(0, priority);
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        return this.computePriority(ctx) > 0;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), SOCIALISE_DURATION_TICKS);
    }

    @Override
    public int cooldownTicks() {
        return SOCIALISE_COOLDOWN_TICKS;
    }
}
