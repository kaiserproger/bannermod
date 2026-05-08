package com.talhanation.bannermod.settlement.civilian.runtime;

import com.talhanation.bannermod.config.WorkersServerConfig;
import com.talhanation.bannermod.persistence.military.RecruitsHireTrade;
import com.talhanation.bannermod.persistence.military.RecruitsHireTradesRegistry;
import com.talhanation.bannermod.registry.civilian.ModEntityTypes;
import net.minecraft.network.chat.Component;

public final class WorkerTradeBootstrap {
    private static final Component TITLE_FARMER = Component.translatable("description.bannermod.workers.title.farmer");
    private static final Component TITLE_MINER = Component.translatable("description.bannermod.workers.title.miner");
    private static final Component TITLE_LUMBERJACK = Component.translatable("description.bannermod.workers.title.lumberjack");
    private static final Component TITLE_BUILDER = Component.translatable("description.bannermod.workers.title.builder");
    private static final Component TITLE_MERCHANT = Component.translatable("description.bannermod.workers.title.merchant");
    private static final Component TITLE_FISHERMAN = Component.translatable("description.bannermod.workers.title.fisherman");
    private static final Component TITLE_ANIMAL_FARMER = Component.translatable("description.bannermod.workers.title.animalFarmer");
    private static final Component DESCRIPTION_FARMER = Component.translatable("description.bannermod.workers.farmer");
    private static final Component DESCRIPTION_MINER = Component.translatable("description.bannermod.workers.miner");
    private static final Component DESCRIPTION_LUMBERJACK = Component.translatable("description.bannermod.workers.lumberjack");
    private static final Component DESCRIPTION_BUILDER = Component.translatable("description.bannermod.workers.builder");
    private static final Component DESCRIPTION_MERCHANT = Component.translatable("description.bannermod.workers.merchant");
    private static final Component DESCRIPTION_FISHERMAN = Component.translatable("description.bannermod.workers.fisherman");
    private static final Component DESCRIPTION_ANIMAL_FARMER = Component.translatable("description.bannermod.workers.animalFarmer");

    private WorkerTradeBootstrap() {
    }

    public static void registerTrades() {
        RecruitsHireTrade farmer = new RecruitsHireTrade(ModEntityTypes.FARMER.getId(), WorkersServerConfig.FarmerCost.get(), TITLE_FARMER, DESCRIPTION_FARMER);
        RecruitsHireTrade lumberjack = new RecruitsHireTrade(ModEntityTypes.LUMBERJACK.getId(), WorkersServerConfig.LumberjackCost.get(), TITLE_LUMBERJACK, DESCRIPTION_LUMBERJACK);
        RecruitsHireTrade miner = new RecruitsHireTrade(ModEntityTypes.MINER.getId(), WorkersServerConfig.MinerCost.get(), TITLE_MINER, DESCRIPTION_MINER);
        RecruitsHireTrade merchant = new RecruitsHireTrade(ModEntityTypes.MERCHANT.getId(), WorkersServerConfig.MerchantCost.get(), TITLE_MERCHANT, DESCRIPTION_MERCHANT);
        RecruitsHireTrade builder = new RecruitsHireTrade(ModEntityTypes.BUILDER.getId(), WorkersServerConfig.BuilderCost.get(), TITLE_BUILDER, DESCRIPTION_BUILDER);
        RecruitsHireTrade fisherman = new RecruitsHireTrade(ModEntityTypes.FISHERMAN.getId(), WorkersServerConfig.BuilderCost.get(), TITLE_FISHERMAN, DESCRIPTION_FISHERMAN);
        RecruitsHireTrade animalFarmer = new RecruitsHireTrade(ModEntityTypes.ANIMAL_FARMER.getId(), WorkersServerConfig.BuilderCost.get(), TITLE_ANIMAL_FARMER, DESCRIPTION_ANIMAL_FARMER);

        RecruitsHireTradesRegistry.addTrade("workers", 1, farmer, lumberjack);
        RecruitsHireTradesRegistry.addTrade("workers", 2, animalFarmer);
        RecruitsHireTradesRegistry.addTrade("workers", 3, builder);

        RecruitsHireTradesRegistry.addTrade("workers2", 1, farmer, miner);
        RecruitsHireTradesRegistry.addTrade("workers2", 2, animalFarmer);
        RecruitsHireTradesRegistry.addTrade("workers2", 3, builder);

        RecruitsHireTradesRegistry.addTrade("workers3", 1, farmer, fisherman);
        RecruitsHireTradesRegistry.addTrade("workers3", 2, animalFarmer);
        RecruitsHireTradesRegistry.addTrade("workers3", 3, builder);
    }
}
