package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.settlement.SettlementResidentAssignmentState;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder for the "deliver goods from workplace to stockpile/market" goal.
 * Wired only for residents with a bound workplace; concrete target resolution
 * lands in slice 25-next-C (project bridge) + 25-next-D (seller dispatch).
 */
public final class DeliverResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/deliver");

    private static final int DELIVER_PRIORITY = 55;
    private static final int DELIVER_DURATION_TICKS = 200;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        return ctx.isActivePhase() ? DELIVER_PRIORITY : 0;
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return false;
        }
        if (ctx.resident().boundWorkAreaUuid() == null) {
            return false;
        }
        return ctx.resident().assignmentState() == SettlementResidentAssignmentState.ASSIGNED_LOCAL_BUILDING;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), DELIVER_DURATION_TICKS);
    }
}
