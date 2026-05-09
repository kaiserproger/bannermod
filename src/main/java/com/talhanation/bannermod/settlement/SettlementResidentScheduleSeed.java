package com.talhanation.bannermod.settlement;

import javax.annotation.Nullable;
import java.util.UUID;

public enum SettlementResidentScheduleSeed {
    SETTLEMENT_IDLE,
    ASSIGNED_WORK,
    GOVERNING;

    public static SettlementResidentScheduleSeed defaultFor(SettlementResidentRole role,
                                                                     @Nullable UUID boundWorkAreaUuid) {
        return switch (role) {
            case VILLAGER -> SETTLEMENT_IDLE;
            case CONTROLLED_WORKER -> boundWorkAreaUuid == null ? SETTLEMENT_IDLE : ASSIGNED_WORK;
            case GOVERNOR_RECRUIT -> GOVERNING;
        };
    }
}
