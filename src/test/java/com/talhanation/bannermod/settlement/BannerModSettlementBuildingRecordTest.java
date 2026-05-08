package com.talhanation.bannermod.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementBuildingRecordTest {

    @Test
    void buildingRecordRoundTripsCategoryAndProfileSeed() {
        SettlementBuildingRecord original = new SettlementBuildingRecord(
                UUID.randomUUID(),
                "bannermod:market_area",
                new BlockPos(12, 64, 12),
                UUID.randomUUID(),
                "blueguild",
                0,
                1,
                1,
                List.of(UUID.randomUUID()),
                true,
                3,
                81,
                true,
                true,
                List.of("food", "materials"),
                SettlementBuildingCategory.MARKET,
                SettlementBuildingProfileSeed.MARKET
        );

        SettlementBuildingRecord restored = SettlementBuildingRecord.fromTag(original.toTag());

        assertEquals(original, restored);
    }

    @Test
    void buildingRecordDefaultsLegacyProfileSeedFromBuildingType() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("BuildingUuid", UUID.randomUUID());
        tag.putString("BuildingTypeId", "bannermod:storage_area");
        tag.putLong("OriginPos", new BlockPos(4, 70, 4).asLong());
        tag.putInt("ResidentCapacity", 0);
        tag.putInt("WorkplaceSlots", 1);
        tag.putInt("AssignedWorkerCount", 0);

        SettlementBuildingRecord restored = SettlementBuildingRecord.fromTag(tag);

        assertEquals(SettlementBuildingCategory.STORAGE, restored.buildingCategory());
        assertEquals(SettlementBuildingProfileSeed.STORAGE, restored.buildingProfileSeed());
    }
}
