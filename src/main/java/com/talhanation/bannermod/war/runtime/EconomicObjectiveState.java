package com.talhanation.bannermod.war.runtime;

public enum EconomicObjectiveState {
    NORMAL,
    CONTESTED,
    BLOCKED,
    RAIDED;

    public EconomicObjectiveState merge(EconomicObjectiveState other) {
        if (other == null || other == NORMAL) {
            return this;
        }
        if (this == NORMAL) {
            return other;
        }
        return priority(other) > priority(this) ? other : this;
    }

    private static int priority(EconomicObjectiveState state) {
        return switch (state) {
            case NORMAL -> 0;
            case CONTESTED -> 1;
            case BLOCKED -> 2;
            case RAIDED -> 3;
        };
    }
}
