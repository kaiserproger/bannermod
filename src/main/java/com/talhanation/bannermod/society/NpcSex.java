package com.talhanation.bannermod.society;

public enum NpcSex {
    UNSPECIFIED,
    MALE,
    FEMALE;

    public static NpcSex fromName(String name) {
        if (name == null || name.isBlank()) {
            return UNSPECIFIED;
        }
        try {
            return NpcSex.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return UNSPECIFIED;
        }
    }
}
