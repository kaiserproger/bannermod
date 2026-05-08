package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BannerModSettlementDesiredGoodsSnapshotTest {

    @Test
    void desiredGoodsSnapshotRoundTripsPersistedDrivers() {
        BannerModSettlementDesiredGoodsSnapshot original = new BannerModSettlementDesiredGoodsSnapshot(List.of(
                new BannerModSettlementDesiredGoodSnapshot("food", 2),
                new BannerModSettlementDesiredGoodSnapshot("storage_type:merchants", 1),
                new BannerModSettlementDesiredGoodSnapshot("market_goods", 3)
        ));

        CompoundTag tag = original.toTag();
        BannerModSettlementDesiredGoodsSnapshot restored = BannerModSettlementDesiredGoodsSnapshot.fromTag(tag);

        assertEquals(original, restored);
    }
}
