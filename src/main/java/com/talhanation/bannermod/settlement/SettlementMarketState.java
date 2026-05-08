package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record SettlementMarketState(
        int marketCount,
        int openMarketCount,
        int totalStorageSlots,
        int freeStorageSlots,
        int sellerDispatchCount,
        int readySellerDispatchCount,
        List<SettlementMarketRecord> markets,
        List<SettlementSellerDispatchRecord> sellerDispatches
) {
    public SettlementMarketState {
        marketCount = Math.max(0, marketCount);
        openMarketCount = Math.max(0, Math.min(openMarketCount, marketCount));
        totalStorageSlots = Math.max(0, totalStorageSlots);
        freeStorageSlots = Math.max(0, Math.min(freeStorageSlots, totalStorageSlots));
        sellerDispatchCount = Math.max(0, sellerDispatchCount);
        readySellerDispatchCount = Math.max(0, Math.min(readySellerDispatchCount, sellerDispatchCount));
        markets = List.copyOf(markets == null ? List.of() : markets);
        sellerDispatches = List.copyOf(sellerDispatches == null ? List.of() : sellerDispatches);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MarketCount", this.marketCount);
        tag.putInt("OpenMarketCount", this.openMarketCount);
        tag.putInt("TotalStorageSlots", this.totalStorageSlots);
        tag.putInt("FreeStorageSlots", this.freeStorageSlots);
        tag.putInt("SellerDispatchCount", this.sellerDispatchCount);
        tag.putInt("ReadySellerDispatchCount", this.readySellerDispatchCount);
        ListTag marketList = new ListTag();
        for (SettlementMarketRecord market : this.markets) {
            marketList.add(market.toTag());
        }
        tag.put("Markets", marketList);
        ListTag sellerDispatchList = new ListTag();
        for (SettlementSellerDispatchRecord sellerDispatch : this.sellerDispatches) {
            sellerDispatchList.add(sellerDispatch.toTag());
        }
        tag.put("SellerDispatches", sellerDispatchList);
        return tag;
    }

    public static SettlementMarketState fromTag(CompoundTag tag) {
        return new SettlementMarketState(
                tag.getInt("MarketCount"),
                tag.getInt("OpenMarketCount"),
                tag.getInt("TotalStorageSlots"),
                tag.getInt("FreeStorageSlots"),
                tag.getInt("SellerDispatchCount"),
                tag.getInt("ReadySellerDispatchCount"),
                readMarkets(tag.getList("Markets", Tag.TAG_COMPOUND)),
                readSellerDispatches(tag.getList("SellerDispatches", Tag.TAG_COMPOUND))
        );
    }

    public static SettlementMarketState empty() {
        return new SettlementMarketState(0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    private static List<SettlementMarketRecord> readMarkets(ListTag list) {
        List<SettlementMarketRecord> markets = new ArrayList<>();
        for (Tag entry : list) {
            markets.add(SettlementMarketRecord.fromTag((CompoundTag) entry));
        }
        return markets;
    }

    private static List<SettlementSellerDispatchRecord> readSellerDispatches(ListTag list) {
        List<SettlementSellerDispatchRecord> sellerDispatches = new ArrayList<>();
        for (Tag entry : list) {
            sellerDispatches.add(SettlementSellerDispatchRecord.fromTag((CompoundTag) entry));
        }
        return sellerDispatches;
    }
}
