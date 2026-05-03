package com.talhanation.bannermod.settlement.goal.impl;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.society.NpcIntent;
import com.talhanation.bannermod.society.NpcSocietyPhaseTwoIntentScorer;
import com.talhanation.bannermod.settlement.goal.ResidentGoal;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;
import com.talhanation.bannermod.settlement.goal.ResidentTask;
import net.minecraft.resources.ResourceLocation;

public final class SeekSuppliesResidentGoal implements ResidentGoal {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "resident/goal/seek_supplies");

    private static final int SEEK_SUPPLIES_DURATION_TICKS = 180;
    private static final int SEEK_SUPPLIES_COOLDOWN_TICKS = 200;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int computePriority(ResidentGoalContext ctx) {
        return NpcSocietyPhaseTwoIntentScorer.scoreIntent(ctx, NpcIntent.SEEK_SUPPLIES);
    }

    @Override
    public boolean canStart(ResidentGoalContext ctx) {
        return this.computePriority(ctx) > 0;
    }

    @Override
    public ResidentTask start(ResidentGoalContext ctx) {
        return new ResidentTask(ID, ctx.gameTime(), SEEK_SUPPLIES_DURATION_TICKS);
    }

    @Override
    public int cooldownTicks() {
        return SEEK_SUPPLIES_COOLDOWN_TICKS;
    }
}
