package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import net.minecraft.resources.ResourceLocation;

/** Lowest-priority fallback. Always startable; runs on a coarse heartbeat. */
public final class IdleResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/idle");

    private static final int IDLE_PRIORITY = 1;
    private static final int IDLE_DURATION_TICKS = 160;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        return IDLE_PRIORITY;
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        return true;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), IDLE_DURATION_TICKS);
    }
}
