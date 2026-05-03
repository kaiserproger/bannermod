package com.talhanation.bannermod.society;

public enum NpcAnchorType {
    NONE,
    HOME,
    WORKPLACE,
    MARKET,
    BARRACKS,
    STREET;

    public static NpcAnchorType fromName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        try {
            return NpcAnchorType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
