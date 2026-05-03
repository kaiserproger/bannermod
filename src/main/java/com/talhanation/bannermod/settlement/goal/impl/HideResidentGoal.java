package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseTwoIntentScorer;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

public final class HideResidentGoal implements ResidentGoal {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/hide");

    private static final int HIDE_DURATION_TICKS = 160;
    private static final int HIDE_COOLDOWN_TICKS = 120;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        return NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.HIDE);
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        return this.computePriority(ctx) > 0;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), HIDE_DURATION_TICKS);
    }

    @Override
    public int cooldownTicks() {
        return HIDE_COOLDOWN_TICKS;
    }
}
