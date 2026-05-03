package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.BannerModSettlementResidentRole;
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
            case SOCIALISE -> scoreSocialise(ctx);
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
        int score = ctx.isRestPhase() ? 92 : 0;
        if (!ctx.isRestPhase() && ctx.fatigueNeed() >= 70) {
            score = Math.max(score, 68 + (ctx.fatigueNeed() - 70));
        }
        if (ctx.safetyNeed() >= 70) {
            score = Math.max(score, 55 + ctx.safetyNeed() / 2);
        }
        return clamp(score);
    }

    private static int scoreRest(ResidentGoalContext ctx) {
        int score = ctx.isRestPhase() ? 86 + ctx.fatigueNeed() / 3 : 0;
        if (ctx.hasHome() && ctx.fatigueNeed() >= 75) {
            score = Math.max(score, 64 + ctx.fatigueNeed() / 2);
        }
        if (ctx.safetyNeed() >= 75 && ctx.hasHome()) {
            score = Math.max(score, 58 + ctx.safetyNeed() / 3);
        }
        return clamp(score);
    }

    private static int scoreEat(ResidentGoalContext ctx) {
        if (!ctx.hasHome() && !hasFoodAccess(ctx)) {
            return 0;
        }
        if (ctx.hungerNeed() < 35) {
            return 0;
        }
        int score = 24 + ctx.hungerNeed();
        score -= ctx.safetyNeed() / 5;
        if (ctx.isRestPhase()) {
            score += 6;
        }
        return clamp(score);
    }

    private static int scoreWork(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return 0;
        }
        int score = 58;
        score -= ctx.fatigueNeed() / 3;
        score -= ctx.hungerNeed() / 4;
        score -= ctx.socialNeed() / 6;
        score -= ctx.safetyNeed() / 2;
        if (ctx.isAdolescent()) {
            score -= 10;
        }
        return clamp(score);
    }

    private static int scoreSeekSupplies(ResidentGoalContext ctx) {
        if (!hasFoodAccess(ctx)) {
            return 0;
        }
        if (ctx.hungerNeed() < 45 || ctx.hasHome()) {
            return 0;
        }
        int score = 20 + ctx.hungerNeed() + ctx.safetyNeed() / 4;
        if (!ctx.isActivePhase()) {
            score -= 10;
        }
        return clamp(score);
    }

    private static int scoreSocialise(ResidentGoalContext ctx) {
        if (!ctx.isActivePhase()) {
            return 0;
        }
        int score = 12 + ctx.socialNeed();
        if (ctx.isAdolescent()) {
            score += 8;
        }
        if (ctx.dayTime() > 9000) {
            score += 6;
        }
        score -= ctx.fatigueNeed() / 4;
        score -= ctx.hungerNeed() / 6;
        score -= ctx.safetyNeed() / 2;
        return clamp(score);
    }

    private static int scoreHide(ResidentGoalContext ctx) {
        if (ctx.safetyNeed() < 40 || ctx.canDefend()) {
            return 0;
        }
        int score = 30 + ctx.safetyNeed();
        if (ctx.hasHome()) {
            score += 10;
        }
        return clamp(score);
    }

    private static int scoreDefend(ResidentGoalContext ctx) {
        if (!ctx.canDefend() || ctx.safetyNeed() < 35) {
            return 0;
        }
        int score = 28 + ctx.safetyNeed();
        if (ctx.resident().role() == BannerModSettlementResidentRole.GOVERNOR_RECRUIT) {
            score += 8;
        }
        return clamp(score);
    }

    private static boolean hasFoodAccess(ResidentGoalContext ctx) {
        return ctx.settlement() != null && ctx.settlement().marketState().openMarketCount() > 0;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(120, score));
    }
}
