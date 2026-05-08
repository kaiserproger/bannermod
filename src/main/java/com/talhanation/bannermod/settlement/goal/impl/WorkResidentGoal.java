package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.settlement.SettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.SettlementResidentRole;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

/**
 * Core day-labor goal for residents that have a workplace assignment. Sits
 * above {@link SocialiseResidentGoal} in priority during active hours so
 * bound workers prefer their job over chatter.
 */
public final class WorkResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/work");

    private static final int WORK_PRIORITY = 60;
    private static final int WORK_DURATION_TICKS = 400;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        return ctx.isActivePhase() ? WORK_PRIORITY : 0;
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return false;
        }
        if (ctx.resident().role() == SettlementResidentRole.GOVERNOR_RECRUIT) {
            return false;
        }
        SettlementResidentAssignmentState state = ctx.resident().assignmentState();
        return state == SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING
                || state == SettlementResidentAssignmentState.ASSIGNED_MISSING_BUILDING;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), WORK_DURATION_TICKS);
    }
}
