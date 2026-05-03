package com.talhanation.bannermod.society;

public enum NpcLivelihoodRequestStatus {
    NONE,
    REQUESTED,
    DENIED,
    APPROVED,
    FULFILLED;

    public static NpcLivelihoodRequestStatus fromName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        try {
            return NpcLivelihoodRequestStatus.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
