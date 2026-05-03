package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseTwoIntentScorer;
import com.talhanation.bannermod.settlement.BannerModSettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

/**
 * Mirror of {@link DeliverResidentGoal} in the opposite direction: fetch
 * inputs from the settlement stockpile to the workplace. Same selection
 * rules, one tick longer to break ties against deliver without ordering
 * ambiguity.
 */
public final class FetchResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/fetch");

    private static final int FETCH_PRIORITY = 55;
    private static final int FETCH_DURATION_TICKS = 220;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return 0;
        }
        return Math.max(FETCH_PRIORITY, NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.WORK) - 3);
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return false;
        }
        if (ctx.resident().boundWorkAreaUuid() == null) {
            return false;
        }
        return ctx.resident().assignmentState() == BannerModSettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), FETCH_DURATION_TICKS);
    }
}
