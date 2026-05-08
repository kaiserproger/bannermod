package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record BannerModSettlementDesiredGoodsSnapshot(
        List<BannerModSettlementDesiredGoodSnapshot> desiredGoods
) {
    public BannerModSettlementDesiredGoodsSnapshot {
        desiredGoods = List.copyOf(desiredGoods == null ? List.of() : desiredGoods);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag desiredGoodsList = new ListTag();
        for (BannerModSettlementDesiredGoodSnapshot desiredGood : this.desiredGoods) {
            desiredGoodsList.add(desiredGood.toTag());
        }
        tag.put("DesiredGoods", desiredGoodsList);
        return tag;
    }

    public static BannerModSettlementDesiredGoodsSnapshot fromTag(CompoundTag tag) {
        List<BannerModSettlementDesiredGoodSnapshot> desiredGoods = new ArrayList<>();
        for (Tag entry : tag.getList("DesiredGoods", Tag.TAG_COMPOUND)) {
            desiredGoods.add(BannerModSettlementDesiredGoodSnapshot.fromTag((CompoundTag) entry));
        }
        return new BannerModSettlementDesiredGoodsSnapshot(desiredGoods);
    }

    public static BannerModSettlementDesiredGoodsSnapshot empty() {
        return new BannerModSettlementDesiredGoodsSnapshot(List.of());
    }
}
