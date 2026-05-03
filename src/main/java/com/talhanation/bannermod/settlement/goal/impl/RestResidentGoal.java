package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

/** High-priority during the rest phase. Keeps residents at home overnight. */
public final class RestResidentGoal implements ResidentGoal {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/rest");

    private static final int REST_PRIORITY = 90;
    private static final int REST_DURATION_TICKS = 200;
    private static final int REST_COOLDOWN_TICKS = 600;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        if (ctx.isRestPhase()) {
            return REST_PRIORITY + ctx.fatigueNeed() / 5;
        }
        if (ctx.hasHome() && ctx.fatigueNeed() >= 80) {
            return 70 + (ctx.fatigueNeed() - 80) / 2;
        }
        return 0;
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        return ctx.isRestPhase() || ctx.hasHome() && ctx.fatigueNeed() >= 80;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), REST_DURATION_TICKS);
    }

    @Override
    public int cooldownTicks() {
        return REST_COOLDOWN_TICKS;
    }
}
