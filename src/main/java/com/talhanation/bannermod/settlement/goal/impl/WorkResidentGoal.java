package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseTwoIntentScorer;
import com.talhanation.bannermod.settlement.SettlementResidentRole;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

/**
 * Core day-labor goal for residents that have a workplace assignment.
 * Bound workers should prefer useful labor during active hours.
 */
public final class WorkResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/work");

    private static final int WORK_PRIORITY = 60;
    private static final int WORK_DURATION_TICKS = 600;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return 0;
        }
        int score = NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.WORK);
        if (score <= 0) {
            return 0;
        }
        return Math.max(WORK_PRIORITY - 10, score);
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return false;
        }
        if (ctx.fatigueNeed() >= 90) {
            return false;
        }
        if (ctx.resident().role() == SettlementResidentRole.GOVERNOR_RECRUIT) {
            return false;
        }
        return ctx.hasWorkAssignment();
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), WORK_DURATION_TICKS);
    }
}
