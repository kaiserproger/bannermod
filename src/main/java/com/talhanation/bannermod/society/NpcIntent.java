package com.talhanation.bannermod.society;

public enum NpcIntent {
    UNSPECIFIED,
    IDLE,
    GO_HOME,
    LEAVE_HOME,
    REST,
    WORK,
    SOCIALISE,
    SELL,
    FETCH,
    DELIVER;

    public static NpcIntent fromName(String name) {
        if (name == null || name.isBlank()) {
            return UNSPECIFIED;
        }
        try {
            return NpcIntent.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return UNSPECIFIED;
        }
    }
}
