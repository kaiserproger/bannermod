package com.talhanation.bannermod.settlement;

import javax.annotation.Nullable;
import java.util.UUID;

public enum SettlementResidentMode {
    SETTLEMENT_RESIDENT,
    PROJECTED_CONTROLLED_WORKER;

    public static SettlementResidentMode fromTagName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return SETTLEMENT_RESIDENT;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return SETTLEMENT_RESIDENT;
        }
    }

    public static SettlementResidentMode defaultFor(SettlementResidentRole role,
                                                             @Nullable UUID ownerUuid) {
        return role == SettlementResidentRole.CONTROLLED_WORKER
                ? PROJECTED_CONTROLLED_WORKER
                : SETTLEMENT_RESIDENT;
    }
}
