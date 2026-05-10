package com.talhanation.bannermod.war.runtime;

public enum EconomicObjectiveType {
    MINE_DISPUTE,
    BLOCKADE,
    RAID,
    OUTPOST_CAPTURE;

    public EconomicObjectiveState economyState() {
        return switch (this) {
            case MINE_DISPUTE, OUTPOST_CAPTURE -> EconomicObjectiveState.CONTESTED;
            case BLOCKADE -> EconomicObjectiveState.BLOCKED;
            case RAID -> EconomicObjectiveState.RAIDED;
        };
    }
}
