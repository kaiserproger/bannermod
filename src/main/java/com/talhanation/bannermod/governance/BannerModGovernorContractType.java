package com.talhanation.bannermod.governance;

public enum BannerModGovernorContractType {
    SUPPLY_RESOURCES("supply_resources"),
    CLEAR_HOSTILES("clear_hostiles"),
    RECRUIT_WORKERS("recruit_workers");

    private final String token;

    BannerModGovernorContractType(String token) {
        this.token = token;
    }

    public String token() {
        return this.token;
    }

    public String displayName() {
        return this.token.replace('_', ' ');
    }

    public static BannerModGovernorContractType fromToken(String token) {
        for (BannerModGovernorContractType type : values()) {
            if (type.token.equals(token)) return type;
        }
        return SUPPLY_RESOURCES;
    }
}
