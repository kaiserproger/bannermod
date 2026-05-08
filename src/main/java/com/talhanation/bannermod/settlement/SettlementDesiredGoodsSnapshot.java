package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record SettlementDesiredGoodsSnapshot(
        List<SettlementDesiredGoodSnapshot> desiredGoods
) {
    public SettlementDesiredGoodsSnapshot {
        desiredGoods = List.copyOf(desiredGoods == null ? List.of() : desiredGoods);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag desiredGoodsList = new ListTag();
        for (SettlementDesiredGoodSnapshot desiredGood : this.desiredGoods) {
            desiredGoodsList.add(desiredGood.toTag());
        }
        tag.put("DesiredGoods", desiredGoodsList);
        return tag;
    }

    public static SettlementDesiredGoodsSnapshot fromTag(CompoundTag tag) {
        List<SettlementDesiredGoodSnapshot> desiredGoods = new ArrayList<>();
        for (Tag entry : tag.getList("DesiredGoods", Tag.TAG_COMPOUND)) {
            desiredGoods.add(SettlementDesiredGoodSnapshot.fromTag((CompoundTag) entry));
        }
        return new SettlementDesiredGoodsSnapshot(desiredGoods);
    }

    public static SettlementDesiredGoodsSnapshot empty() {
        return new SettlementDesiredGoodsSnapshot(List.of());
    }
}
