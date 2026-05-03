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
                || intent == NpcIntent.SOCIALISE
                || intent == NpcIntent.HIDE
                || intent == NpcIntent.DEFEND;
    }
}
