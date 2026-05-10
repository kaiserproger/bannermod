package com.talhanation.bannermod.society;

import com.talhanation.bannermod.settlement.SettlementBuildingProfileSeed;
import com.talhanation.bannermod.settlement.prefab.impl.AnimalPenPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.LumberCampPrefab;
import com.talhanation.bannermod.settlement.prefab.impl.MinePrefab;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public enum NpcLivelihoodRequestType {
    LUMBER_CAMP(LumberCampPrefab.ID, SettlementBuildingProfileSeed.MATERIAL_PRODUCTION),
    MINE(MinePrefab.ID, SettlementBuildingProfileSeed.MATERIAL_PRODUCTION),
    ANIMAL_PEN(AnimalPenPrefab.ID, SettlementBuildingProfileSeed.FOOD_PRODUCTION);

    private final ResourceLocation prefabId;
    private final SettlementBuildingProfileSeed profileSeed;

    NpcLivelihoodRequestType(ResourceLocation prefabId,
                             SettlementBuildingProfileSeed profileSeed) {
        this.prefabId = prefabId;
        this.profileSeed = profileSeed;
    }

    public ResourceLocation prefabId() {
        return this.prefabId;
    }

    public SettlementBuildingProfileSeed profileSeed() {
        return this.profileSeed;
    }

    public String translationSuffix() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static @Nullable NpcLivelihoodRequestType fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return NpcLivelihoodRequestType.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
