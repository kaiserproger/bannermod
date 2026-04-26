package com.talhanation.bannermod.governance;

public enum BannerModGovernorContractStatus {
    OPEN, ACCEPTED, COMPLETED, CANCELLED, EXPIRED;

    public static BannerModGovernorContractStatus fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
