package com.talhanation.bannermod.settlement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementBuildingProfileSeedTest {

    @Test
    void categoryMatchesExpectedEnumBuckets() {
        assertEquals(SettlementBuildingCategory.FOOD, SettlementBuildingProfileSeed.FOOD_PRODUCTION.category());
        assertEquals(SettlementBuildingCategory.MATERIAL, SettlementBuildingProfileSeed.MATERIAL_PRODUCTION.category());
        assertEquals(SettlementBuildingCategory.STORAGE, SettlementBuildingProfileSeed.STORAGE.category());
        assertEquals(SettlementBuildingCategory.MARKET, SettlementBuildingProfileSeed.MARKET.category());
        assertEquals(SettlementBuildingCategory.CONSTRUCTION, SettlementBuildingProfileSeed.CONSTRUCTION.category());
        assertEquals(SettlementBuildingCategory.GENERAL, SettlementBuildingProfileSeed.GENERAL.category());
    }

    @Test
    void fromBuildingTypeIdMapsKnownPathsAndFallsBackToGeneral() {
        assertEquals(SettlementBuildingProfileSeed.GENERAL,
                SettlementBuildingProfileSeed.fromBuildingTypeId(null));
        assertEquals(SettlementBuildingProfileSeed.GENERAL,
                SettlementBuildingProfileSeed.fromBuildingTypeId(" "));
        assertEquals(SettlementBuildingProfileSeed.FOOD_PRODUCTION,
                SettlementBuildingProfileSeed.fromBuildingTypeId("bannermod:crop_area"));
        assertEquals(SettlementBuildingProfileSeed.FOOD_PRODUCTION,
                SettlementBuildingProfileSeed.fromBuildingTypeId("animal_pen_area"));
        assertEquals(SettlementBuildingProfileSeed.MATERIAL_PRODUCTION,
                SettlementBuildingProfileSeed.fromBuildingTypeId("bannermod:mining_area"));
        assertEquals(SettlementBuildingProfileSeed.STORAGE,
                SettlementBuildingProfileSeed.fromBuildingTypeId("storage_area"));
        assertEquals(SettlementBuildingProfileSeed.MARKET,
                SettlementBuildingProfileSeed.fromBuildingTypeId("market_area"));
        assertEquals(SettlementBuildingProfileSeed.CONSTRUCTION,
                SettlementBuildingProfileSeed.fromBuildingTypeId("build_area"));
        assertEquals(SettlementBuildingProfileSeed.GENERAL,
                SettlementBuildingProfileSeed.fromBuildingTypeId("bannermod:watchtower"));
    }

    @Test
    void fromTagNameFallsBackToGeneralForBlankOrUnknownValues() {
        assertEquals(SettlementBuildingProfileSeed.GENERAL,
                SettlementBuildingProfileSeed.fromTagName(null));
        assertEquals(SettlementBuildingProfileSeed.GENERAL,
                SettlementBuildingProfileSeed.fromTagName(""));
        assertEquals(SettlementBuildingProfileSeed.GENERAL,
                SettlementBuildingProfileSeed.fromTagName("NOT_REAL"));
        assertEquals(SettlementBuildingProfileSeed.MARKET,
                SettlementBuildingProfileSeed.fromTagName("MARKET"));
    }
}
