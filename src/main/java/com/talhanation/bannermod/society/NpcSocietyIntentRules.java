package com.talhanation.bannermod.society;

import javax.annotation.Nullable;

public final class NpcSocietyIntentRules {
    private NpcSocietyIntentRules() {
    }

    public static boolean isWorkerLaborIntent(@Nullable NpcIntent intent) {
        return intent == null
                || intent == NpcIntent.UNSPECIFIED
                || intent == NpcIntent.WORK
                || intent == NpcIntent.SELL
                || intent == NpcIntent.FETCH
                || intent == NpcIntent.DELIVER;
    }

    public static boolean isWorkFamilyIntent(@Nullable NpcIntent intent) {
        return intent == NpcIntent.WORK
                || intent == NpcIntent.SELL
                || intent == NpcIntent.FETCH
                || intent == NpcIntent.DELIVER;
    }

    public static boolean isRoutineDailyIntent(@Nullable NpcIntent intent) {
        return isWorkFamilyIntent(intent)
                || intent == NpcIntent.SEEK_SUPPLIES;
    }

    public static boolean isSafeRecoveryIntent(@Nullable NpcIntent intent) {
        return intent == NpcIntent.GO_HOME
                || intent == NpcIntent.REST
                || intent == NpcIntent.EAT
                || intent == NpcIntent.SEEK_SUPPLIES
                || intent == NpcIntent.HIDE;
    }

    public static boolean isRestLikeIntent(@Nullable NpcIntent intent) {
        return intent == NpcIntent.GO_HOME
                || intent == NpcIntent.REST
                || intent == NpcIntent.HIDE;
    }

    public static boolean isAnchoredRoutineIntent(@Nullable NpcIntent intent) {
        return intent == NpcIntent.GO_HOME
                || intent == NpcIntent.LEAVE_HOME
                || intent == NpcIntent.REST
                || intent == NpcIntent.EAT
                || intent == NpcIntent.SEEK_SUPPLIES
                || intent == NpcIntent.HIDE
                || intent == NpcIntent.DEFEND;
    }

    public static boolean sharesFailureRetryFamily(@Nullable NpcIntent failedIntent, @Nullable NpcIntent nextIntent) {
        if (failedIntent == null || nextIntent == null || failedIntent == NpcIntent.UNSPECIFIED || nextIntent == NpcIntent.UNSPECIFIED) {
            return false;
        }
        if (failedIntent == nextIntent) {
            return true;
        }
        return isWorkFamilyIntent(failedIntent) && isWorkFamilyIntent(nextIntent);
    }
}
