package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record SettlementTradeRouteHandoffSnapshot(
        int sellerDispatchCount,
        int readySellerDispatchCount,
        int routedStorageCount,
        int portEntrypointCount,
        int activeReservationCount,
        int reservedUnitCount,
        List<SettlementDesiredGoodSnapshot> desiredGoods,
        List<SettlementSellerDispatchRecord> sellerDispatches,
        List<String> seaTradeStatusLines
) {
    public SettlementTradeRouteHandoffSnapshot {
        sellerDispatchCount = Math.max(0, sellerDispatchCount);
        readySellerDispatchCount = Math.max(0, Math.min(readySellerDispatchCount, sellerDispatchCount));
        routedStorageCount = Math.max(0, routedStorageCount);
        portEntrypointCount = Math.max(0, portEntrypointCount);
        activeReservationCount = Math.max(0, activeReservationCount);
        reservedUnitCount = Math.max(0, reservedUnitCount);
        desiredGoods = List.copyOf(desiredGoods == null ? List.of() : desiredGoods);
        sellerDispatches = List.copyOf(sellerDispatches == null ? List.of() : sellerDispatches);
        seaTradeStatusLines = List.copyOf(seaTradeStatusLines == null ? List.of() : seaTradeStatusLines);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SellerDispatchCount", this.sellerDispatchCount);
        tag.putInt("ReadySellerDispatchCount", this.readySellerDispatchCount);
        tag.putInt("RoutedStorageCount", this.routedStorageCount);
        tag.putInt("PortEntrypointCount", this.portEntrypointCount);
        tag.putInt("ActiveReservationCount", this.activeReservationCount);
        tag.putInt("ReservedUnitCount", this.reservedUnitCount);

        ListTag desiredGoodsList = new ListTag();
        for (SettlementDesiredGoodSnapshot desiredGood : this.desiredGoods) {
            desiredGoodsList.add(desiredGood.toTag());
        }
        tag.put("DesiredGoods", desiredGoodsList);

        ListTag sellerDispatchList = new ListTag();
        for (SettlementSellerDispatchRecord sellerDispatch : this.sellerDispatches) {
            sellerDispatchList.add(sellerDispatch.toTag());
        }
        tag.put("SellerDispatches", sellerDispatchList);

        ListTag seaTradeStatusList = new ListTag();
        for (String line : this.seaTradeStatusLines) {
            seaTradeStatusList.add(StringTag.valueOf(line));
        }
        tag.put("SeaTradeStatusLines", seaTradeStatusList);
        return tag;
    }

    public static SettlementTradeRouteHandoffSnapshot fromTag(CompoundTag tag) {
        return new SettlementTradeRouteHandoffSnapshot(
                tag.getInt("SellerDispatchCount"),
                tag.getInt("ReadySellerDispatchCount"),
                tag.getInt("RoutedStorageCount"),
                tag.getInt("PortEntrypointCount"),
                tag.getInt("ActiveReservationCount"),
                tag.getInt("ReservedUnitCount"),
                readDesiredGoods(tag.getList("DesiredGoods", Tag.TAG_COMPOUND)),
                readSellerDispatches(tag.getList("SellerDispatches", Tag.TAG_COMPOUND)),
                readStrings(tag.getList("SeaTradeStatusLines", Tag.TAG_STRING))
        );
    }

    public static SettlementTradeRouteHandoffSnapshot empty() {
        return new SettlementTradeRouteHandoffSnapshot(0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }

    private static List<SettlementDesiredGoodSnapshot> readDesiredGoods(ListTag list) {
        List<SettlementDesiredGoodSnapshot> desiredGoods = new ArrayList<>();
        for (Tag entry : list) {
            desiredGoods.add(SettlementDesiredGoodSnapshot.fromTag((CompoundTag) entry));
        }
        return desiredGoods;
    }

    private static List<SettlementSellerDispatchRecord> readSellerDispatches(ListTag list) {
        List<SettlementSellerDispatchRecord> sellerDispatches = new ArrayList<>();
        for (Tag entry : list) {
            sellerDispatches.add(SettlementSellerDispatchRecord.fromTag((CompoundTag) entry));
        }
        return sellerDispatches;
    }

    private static List<String> readStrings(ListTag list) {
        List<String> values = new ArrayList<>();
        for (Tag entry : list) {
            values.add(entry.getAsString());
        }
        return values;
    }
}
