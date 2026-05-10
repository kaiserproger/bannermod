package com.talhanation.bannermod.settlement.economy;

public enum StrategicResourceBucket {
    FOOD("food"),
    IRON("iron"),
    WOOD("wood"),
    STONE("stone"),
    COINS("coins");

    private final String id;

    StrategicResourceBucket(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public boolean treasuryBacked() {
        return this == COINS;
    }
}
