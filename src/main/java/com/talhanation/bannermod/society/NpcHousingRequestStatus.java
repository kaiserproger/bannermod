package com.talhanation.bannermod.society;

public enum NpcHousingRequestStatus {
    NONE,
    REQUESTED,
    APPROVED,
    FULFILLED;

    public static NpcHousingRequestStatus fromName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        try {
            return NpcHousingRequestStatus.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
