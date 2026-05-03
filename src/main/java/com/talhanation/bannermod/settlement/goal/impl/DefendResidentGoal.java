package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseTwoIntentScorer;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

public final class DefendResidentGoal implements ResidentGoal {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/defend");

    private static final int DEFEND_DURATION_TICKS = 100;
    private static final int DEFEND_COOLDOWN_TICKS = 80;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        return NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.DEFEND);
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        return ctx.canDefend() && this.computePriority(ctx) > 0;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), DEFEND_DURATION_TICKS);
    }

    @Override
    public int cooldownTicks() {
        return DEFEND_COOLDOWN_TICKS;
    }
}
