package com.talhanation.bannermod.settlement;

public enum SettlementServiceActorState {
    NOT_SERVICE_ACTOR,
    LOCAL_BUILDING_SERVICE,
    FLOATING_SERVICE,
    ORPHANED_SERVICE;

    public static SettlementServiceActorState fromTagName(String name) {
        if (name == null || name.isBlank()) {
            return NOT_SERVICE_ACTOR;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NOT_SERVICE_ACTOR;
        }
    }
}
