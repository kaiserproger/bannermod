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
        if (ctx.isReadyToSettleAtHome()) {
            score -= 20;
        }
        if (ctx.isLateDayWindow(1000)) {
            int eveningPull = 36 + (1000 - ctx.ticksUntilRestStart()) / 25;
            score = Math.max(score, eveningPull);
        }
        if (ctx.isLeisurePhase() && ctx.fatigueNeed() >= 55) {
            score = Math.max(score, 30 + ctx.fatigueNeed() / 2);
        }
        if (!ctx.isRestPhase() && ctx.fatigueNeed() >= 70) {
            score = Math.max(score, 68 + (ctx.fatigueNeed() - 70));
        }
        if (ctx.safetyNeed() >= 70) {
            score = Math.max(score, 55 + ctx.safetyNeed() / 2);
        }
        if (ctx.hasFamilyTies()) {
            score += ctx.hasDependents() ? 8 : 4;
        }
        if (ctx.isHouseholdPressured()) {
            score += 5;
        }
        if (ctx.fearScore() >= 60) {
            score += 8;
        }
        score += ctx.fearScore() / 5;
        return clamp(score);
    }

    private static int scoreRest(ResidentGoalContext ctx) {
        int score = ctx.isRestPhase() ? 86 + ctx.fatigueNeed() / 3 : 0;
        if (ctx.isReadyToSettleAtHome()) {
            score = Math.max(score, 112);
        }
        if (ctx.lastPublishedIntent() == NpcIntent.GO_HOME && ctx.isRestPhase()) {
            score += 8;
        }
        if (ctx.hasHome() && ctx.fatigueNeed() >= 75) {
            score = Math.max(score, 64 + ctx.fatigueNeed() / 2);
        }
        if (ctx.safetyNeed() >= 75 && ctx.hasHome()) {
            score = Math.max(score, 58 + ctx.safetyNeed() / 3);
        }
        if (ctx.hasFamilyTies() && ctx.hasHome()) {
            score += ctx.hasDependents() ? 6 : 3;
        }
        score += ctx.fearScore() / 6;
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
        score += Math.min(8, ctx.householdSize() * 2);
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
        if (ctx.isEarlyActiveWindow(500) && ctx.hasHome()) {
            score -= 6;
        }
        if (ctx.isReadyToFanOutFromLeaveHome()) {
            score += 10;
        }
        score -= ctx.fatigueNeed() / 3;
        score -= ctx.hungerNeed() / 4;
        score -= ctx.socialNeed() / 6;
        score -= ctx.safetyNeed() / 2;
        score += ctx.loyaltyScore() / 6;
        score += ctx.trustScore() / 10;
        score += ctx.gratitudeScore() / 14;
        score -= ctx.angerScore() / 6;
        score -= ctx.fearScore() / 8;
        if (ctx.isHouseholdPressured()) {
            score += ctx.isHomelessHousehold() ? 10 : 6;
        }
        if (ctx.hasDependents()) {
            score += 5;
        }
        if (ctx.fearScore() >= 60) {
            score -= 8;
        }
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
        score += Math.min(10, ctx.householdSize() * 2);
        if (!ctx.isActivePhase()) {
            score -= 10;
        }
        return clamp(score);
    }

    private static int scoreSocialise(ResidentGoalContext ctx) {
        if (!ctx.isDayRoutinePhase()) {
            return 0;
        }
        int score = 12 + ctx.socialNeed();
        if (ctx.isLeisurePhase()) {
            score += 16;
        } else if (ctx.isEarlyActiveWindow(800)) {
            score -= 8;
        }
        if (ctx.isReadyToFanOutFromLeaveHome()) {
            score += 6;
        }
        if (ctx.isAdolescent()) {
            score += 8;
        }
        if (ctx.hasFamilyTies()) {
            score += ctx.hasDependents() ? 6 : 3;
        }
        if (ctx.isLeisurePhase() && ctx.hasHome() && ctx.hasFamilyTies()) {
            score += 8;
        }
        if (ctx.dayTime() > 9000) {
            score += 6;
        }
        score += ctx.trustScore() / 10;
        score += ctx.gratitudeScore() / 12;
        score -= ctx.fatigueNeed() / 4;
        score -= ctx.hungerNeed() / 6;
        score -= ctx.safetyNeed() / 2;
        score -= ctx.fearScore() / 4;
        score -= ctx.angerScore() / 5;
        return clamp(applyIntentHistory(ctx, NpcIntent.SOCIALISE, score, 8));
    }

    private static int scoreHide(ResidentGoalContext ctx) {
        int dangerPressure = Math.max(ctx.safetyNeed(), ctx.fearScore());
        if (dangerPressure < 35 || ctx.canDefend() && ctx.angerScore() > ctx.fearScore() + 12) {
            return 0;
        }
        int score = 24 + dangerPressure + ctx.fearScore() / 3 - ctx.angerScore() / 7;
        if (ctx.hasFamilyTies()) {
            score += ctx.hasDependents() ? 10 : 5;
        }
        if (ctx.isHouseholdPressured()) {
            score += 5;
        }
        if (ctx.hasHome()) {
            score += 10;
        }
        return clamp(score);
    }

    private static int scoreDefend(ResidentGoalContext ctx) {
        int defendPressure = Math.max(ctx.safetyNeed(), ctx.angerScore());
        if (!ctx.canDefend() || defendPressure < 30) {
            return 0;
        }
        int score = 20 + defendPressure + ctx.angerScore() / 2 + ctx.loyaltyScore() / 5 - ctx.fearScore() / 6;
        if (ctx.hasFamilyTies()) {
            score += ctx.hasDependents() ? 12 : 6;
        }
        if (ctx.isHouseholdPressured()) {
            score += 4;
        }
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
