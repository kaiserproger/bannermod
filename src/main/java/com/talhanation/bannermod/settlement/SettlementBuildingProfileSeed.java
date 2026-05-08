package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AnimalPenArea;
import com.talhanation.bannermod.entity.civilian.workarea.BuildArea;
import com.talhanation.bannermod.entity.civilian.workarea.CropArea;
import com.talhanation.bannermod.entity.civilian.workarea.FishingArea;
import com.talhanation.bannermod.entity.civilian.workarea.LumberArea;
import com.talhanation.bannermod.entity.civilian.workarea.MarketArea;
import com.talhanation.bannermod.entity.civilian.workarea.MiningArea;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import net.minecraft.resources.ResourceLocation;

public enum SettlementBuildingProfileSeed {
    FOOD_PRODUCTION(SettlementBuildingCategory.FOOD),
    MATERIAL_PRODUCTION(SettlementBuildingCategory.MATERIAL),
    STORAGE(SettlementBuildingCategory.STORAGE),
    MARKET(SettlementBuildingCategory.MARKET),
    CONSTRUCTION(SettlementBuildingCategory.CONSTRUCTION),
    GENERAL(SettlementBuildingCategory.GENERAL);

    private final SettlementBuildingCategory category;

    SettlementBuildingProfileSeed(SettlementBuildingCategory category) {
        this.category = category;
    }

    public SettlementBuildingCategory category() {
        return this.category;
    }

    public static SettlementBuildingProfileSeed fromWorkArea(AbstractWorkAreaEntity workArea) {
        if (workArea instanceof CropArea || workArea instanceof AnimalPenArea || workArea instanceof FishingArea) {
            return FOOD_PRODUCTION;
        }
        if (workArea instanceof LumberArea || workArea instanceof MiningArea) {
            return MATERIAL_PRODUCTION;
        }
        if (workArea instanceof StorageArea) {
            return STORAGE;
        }
        if (workArea instanceof MarketArea) {
            return MARKET;
        }
        if (workArea instanceof BuildArea) {
            return CONSTRUCTION;
        }
        return GENERAL;
    }

    public static SettlementBuildingProfileSeed fromBuildingTypeId(String buildingTypeId) {
        if (buildingTypeId == null || buildingTypeId.isBlank()) {
            return GENERAL;
        }

        ResourceLocation typeId = ResourceLocation.tryParse(buildingTypeId);
        String path = typeId == null ? buildingTypeId : typeId.getPath();
        return switch (path) {
            case "crop_area", "animal_pen_area", "fishing_area" -> FOOD_PRODUCTION;
            case "lumber_area", "mining_area" -> MATERIAL_PRODUCTION;
            case "storage_area" -> STORAGE;
            case "market_area" -> MARKET;
            case "build_area" -> CONSTRUCTION;
            default -> GENERAL;
        };
    }

    public static SettlementBuildingProfileSeed fromTagName(String name) {
        if (name == null || name.isBlank()) {
            return GENERAL;
        }
        try {
            return SettlementBuildingProfileSeed.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return GENERAL;
        }
    }
}
