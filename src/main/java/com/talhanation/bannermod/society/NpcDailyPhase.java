package com.talhanation.bannermod.society;

public enum NpcDailyPhase {
    UNSPECIFIED,
    ACTIVE,
    RETURNING_HOME,
    REST;

    public static NpcDailyPhase fromName(String name) {
        if (name == null || name.isBlank()) {
            return UNSPECIFIED;
        }
        try {
            return NpcDailyPhase.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return UNSPECIFIED;
        }
    }
}
