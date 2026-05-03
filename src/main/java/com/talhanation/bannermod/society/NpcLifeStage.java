package com.talhanation.bannermod.society;

public enum NpcLifeStage {
    UNSPECIFIED,
    CHILD,
    ADOLESCENT,
    ADULT,
    ELDER;

    public static NpcLifeStage fromName(String name) {
        if (name == null || name.isBlank()) {
            return UNSPECIFIED;
        }
        try {
            return NpcLifeStage.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return UNSPECIFIED;
        }
    }
}
