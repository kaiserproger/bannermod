package com.talhanation.bannermod.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class BannerModGovernorContractConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SERVER;

    public static final ForgeConfigSpec.IntValue DEFAULT_MAX_REWARD;
    public static final ForgeConfigSpec.IntValue SUPPLY_RESOURCES_BASE_REWARD;
    public static final ForgeConfigSpec.IntValue CLEAR_HOSTILES_BASE_REWARD;
    public static final ForgeConfigSpec.IntValue RECRUIT_WORKERS_BASE_REWARD;
    public static final ForgeConfigSpec.IntValue MAX_OPEN_CONTRACTS_PER_CLAIM;
    public static final ForgeConfigSpec.LongValue CONTRACT_DEADLINE_TICKS;

    static {
        BUILDER.comment("BannerMod Governor Contract System configuration");

        DEFAULT_MAX_REWARD = BUILDER
                .comment("Server default max reward cap when the claim owner has not set one (treasury units)")
                .defineInRange("DefaultMaxReward", 100, 1, 10000);

        SUPPLY_RESOURCES_BASE_REWARD = BUILDER
                .comment("Base reward paid for completing a supply-resources contract")
                .defineInRange("SupplyResourcesBaseReward", 20, 1, 10000);

        CLEAR_HOSTILES_BASE_REWARD = BUILDER
                .comment("Base reward paid for clearing hostile mobs or NPCs from a claim")
                .defineInRange("ClearHostilesBaseReward", 50, 1, 10000);

        RECRUIT_WORKERS_BASE_REWARD = BUILDER
                .comment("Base reward paid for a worker-recruitment contract")
                .defineInRange("RecruitWorkersBaseReward", 30, 1, 10000);

        MAX_OPEN_CONTRACTS_PER_CLAIM = BUILDER
                .comment("Maximum number of OPEN contracts allowed per claim at the same time")
                .defineInRange("MaxOpenContractsPerClaim", 5, 1, 20);

        CONTRACT_DEADLINE_TICKS = BUILDER
                .comment("How many ticks before an unpinned open contract expires (72000 = 3 in-game days)")
                .defineInRange("ContractDeadlineTicks", 72000L, 1L, Long.MAX_VALUE);

        SERVER = BUILDER.build();
    }
}
