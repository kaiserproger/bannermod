package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.SettlementResidentRole;
import com.talhanation.bannermod.settlement.goal.ResidentGoalContext;

public final class NpcSocietyPhaseTwoIntentScorer {
    private NpcSocietyPhaseTwoIntentScorer() {
    }

    public static int scoreIntent(ResidentGoalContext ctx, NpcIntent intent) {
        if (ctx == null || intent == null) {
            return 0;
        }
        return switch (intent) {
            case GO_HOME -> scoreGoHome(ctx);
            case REST -> scoreRest(ctx);
            case EAT -> scoreEat(ctx);
            case WORK -> scoreWork(ctx);
            case SEEK_SUPPLIES -> scoreSeekSupplies(ctx);
            case HIDE -> scoreHide(ctx);
            case DEFEND -> scoreDefend(ctx);
            case IDLE -> 1;
            default -> 0;
        };
    }

    private static int scoreGoHome(ResidentGoalContext ctx) {
        if (!ctx.hasHome()) {
            return 0;
        }
        int score = 0;
        if (ctx.isRestPhase()) {
            score = 84;
        }
        if (ctx.isLateDayWindow(1000)) {
            score = Math.max(score, 68);
        }
        if (ctx.fatigueNeed() >= 80) {
            score = Math.max(score, 76 + Math.max(0, ctx.fatigueNeed() - 80) / 2);
        }
        return clamp(applyIntentHistory(ctx, NpcIntent.GO_HOME, score, 4));
    }

    private static int scoreRest(ResidentGoalContext ctx) {
        int score = ctx.isRestPhase() ? 86 + ctx.fatigueNeed() / 4 : 0;
        if (ctx.isReadyToSettleAtHome()) {
            score = Math.max(score, 112);
        }
        if (ctx.hasHome() && ctx.fatigueNeed() >= 85) {
            score = Math.max(score, 72 + Math.max(0, ctx.fatigueNeed() - 85) / 2);
        }
        return clamp(applyIntentHistory(ctx, NpcIntent.REST, score, 4));
    }

    private static int scoreEat(ResidentGoalContext ctx) {
        if (!ctx.hasHome() && !ctx.hasMarketFoodAccess()) {
            return 0;
        }
        if (ctx.hungerNeed() < 70) {
            return 0;
        }
        int score = 48 + ctx.hungerNeed() / 2;
        if (ctx.hungerNeed() >= 85) {
            score += 12;
        }
        score -= ctx.safetyNeed() / 4;
        if (ctx.isRestPhase()) {
            score += 4;
        }
        return clamp(applyIntentHistory(ctx, NpcIntent.EAT, score, 4));
    }

    private static int scoreWork(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase() || !ctx.hasWorkAssignment()) {
            return 0;
        }
        if (ctx.isLeisurePhase() || ctx.isLateDayWindow(1200)) {
            return 0;
        }
        if (ctx.fatigueNeed() >= 85 || ctx.hungerNeed() >= 80 || ctx.safetyNeed() >= 35) {
            return 0;
        }
        int score = 72;
        score -= ctx.fatigueNeed() / 3;
        score -= ctx.hungerNeed() / 4;
        score -= ctx.safetyNeed();
        if (ctx.isAdolescent()) {
            score -= 10;
        }
        return clamp(applyIntentHistory(ctx, NpcIntent.WORK, score, 4));
    }

    private static int scoreSeekSupplies(ResidentGoalContext ctx) {
        if (!ctx.hasSupplyAccess()) {
            return 0;
        }
        if (ctx.hungerNeed() < 70) {
            return 0;
        }
        if (!ctx.shouldEscalateMealRecoveryToSupplies()) {
            return 0;
        }
        int score = 54 + ctx.hungerNeed() / 2 - ctx.safetyNeed() / 4;
        if (!ctx.isActivePhase()) {
            score -= 6;
        }
        return clamp(applyIntentHistory(ctx, NpcIntent.SEEK_SUPPLIES, score, 4));
    }

    private static int scoreHide(ResidentGoalContext ctx) {
        int dangerPressure = ctx.safetyNeed();
        if (dangerPressure < 35) {
            return 0;
        }
        int score = 100 + Math.max(0, dangerPressure - 35) / 2;
        if (ctx.hasHome()) {
            score += 4;
        }
        return clamp(applyIntentHistory(ctx, NpcIntent.HIDE, score, 4));
    }

    private static int scoreDefend(ResidentGoalContext ctx) {
        int defendPressure = ctx.safetyNeed();
        if (!ctx.canDefend() || defendPressure < 35) {
            return 0;
        }
        int score = 88 + defendPressure / 2;
        if (ctx.resident().role() == SettlementResidentRole.GOVERNOR_RECRUIT) {
            score += 4;
        }
        return clamp(applyIntentHistory(ctx, NpcIntent.DEFEND, score, 6));
    }
    private static int clamp(int score) {
        return Math.max(0, Math.min(120, score));
    }

    private static int applyIntentHistory(ResidentGoalContext ctx, NpcIntent intent, int score, int maxBonus) {
        if (ctx == null || intent == null || score <= 0) {
            return score;
        }
        if (ctx.currentPublishedIntent() != intent) {
            return score;
        }
        long age = ctx.currentIntentAgeTicks();
        int bonus = (int) Math.max(0L, maxBonus - age / 80L);
        return score + bonus;
    }
}
