package com.talhanation.bannermod.entity.military.runtime;

import com.talhanation.bannermod.registry.military.ModBlocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;
import java.util.Map;

public final class VillagerProfessionTradeRegistrationService {
    private static final int JOURNEYMAN_LEVEL = 2;
    private static final int MAX_USES = 2;
    private static final int GIVEN_EXPERIENCE = 20;
    private static final Map<VillagerProfession, List<VillagerTrades.ItemListing>> BLOCK_TRADES_BY_PROFESSION = Map.of(
            VillagerProfession.ARMORER, List.of(new BlockTrade(Items.EMERALD, 15, ModBlocks.RECRUIT_SHIELD_BLOCK.get())),
            VillagerProfession.WEAPONSMITH, List.of(new BlockTrade(Items.EMERALD, 8, ModBlocks.RECRUIT_BLOCK.get())),
            VillagerProfession.FLETCHER, List.of(
                    new BlockTrade(Items.EMERALD, 10, ModBlocks.BOWMAN_BLOCK.get()),
                    new BlockTrade(Items.EMERALD, 20, ModBlocks.CROSSBOWMAN_BLOCK.get())
            ),
            VillagerProfession.CARTOGRAPHER, List.of(new BlockTrade(Items.EMERALD, 30, ModBlocks.NOMAD_BLOCK.get())),
            VillagerProfession.BUTCHER, List.of(new BlockTrade(Items.EMERALD, 30, ModBlocks.HORSEMAN_BLOCK.get()))
    );

    private VillagerProfessionTradeRegistrationService() {
    }

    public static void registerProfessionBlockTrades(VillagerTradesEvent event) {
        List<VillagerTrades.ItemListing> blockTrades = BLOCK_TRADES_BY_PROFESSION.get(event.getType());
        if (blockTrades == null) {
            return;
        }

        event.getTrades().get(JOURNEYMAN_LEVEL).addAll(blockTrades);
    }

    private static final class BlockTrade implements VillagerTrades.ItemListing {
        private static final float PRICE_MULTIPLIER = 0.05F;

        private final Item buyingItem;
        private final Item sellingItem;
        private final int buyingAmount;

        private BlockTrade(ItemLike buyingItem, int buyingAmount, ItemLike sellingItem) {
            this.buyingItem = buyingItem.asItem();
            this.buyingAmount = buyingAmount;
            this.sellingItem = sellingItem.asItem();
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            return new MerchantOffer(
                    new ItemCost(this.buyingItem, this.buyingAmount),
                    new ItemStack(this.sellingItem, 1),
                    MAX_USES,
                    GIVEN_EXPERIENCE,
                    PRICE_MULTIPLIER
            );
        }
    }
}
