package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementDesiredGoodsSnapshotTest {

    @Test
    void desiredGoodsSnapshotRoundTripsPersistedDrivers() {
        SettlementDesiredGoodsSnapshot original = new SettlementDesiredGoodsSnapshot(List.of(
                new SettlementDesiredGoodSnapshot("food", 2),
                new SettlementDesiredGoodSnapshot("storage_type:merchants", 1),
                new SettlementDesiredGoodSnapshot("market_goods", 3)
        ));

        CompoundTag tag = original.toTag();
        SettlementDesiredGoodsSnapshot restored = SettlementDesiredGoodsSnapshot.fromTag(tag);

        assertEquals(original, restored);
    }
}
