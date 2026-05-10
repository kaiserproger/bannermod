package com.talhanation.bannermod.compat.venaterra;

public enum VenaterraDepositCategory {
    IRON,
    INDUSTRIAL_FUEL,
    PRECIOUS_COIN_VALUE,
    QUARRY_STONE,
    UNKNOWN_OTHER;

    static VenaterraDepositCategory fromApiCategory(Object apiCategory) {
        if (apiCategory == null) {
            return UNKNOWN_OTHER;
        }

        try {
            return valueOf(apiCategory.toString());
        }
        catch (IllegalArgumentException ignored) {
            return UNKNOWN_OTHER;
        }
    }
}
