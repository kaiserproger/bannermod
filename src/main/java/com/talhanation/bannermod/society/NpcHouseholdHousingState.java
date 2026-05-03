package com.talhanation.bannermod.society;

public enum NpcHouseholdHousingState {
    NORMAL,
    HOMELESS,
    OVERCROWDED;

    public static NpcHouseholdHousingState fromName(String name) {
        if (name == null || name.isBlank()) {
            return HOMELESS;
        }
        try {
            return NpcHouseholdHousingState.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return HOMELESS;
        }
    }
}
