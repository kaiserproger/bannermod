package com.talhanation.bannermod.compat.venaterra;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record VenaterraDepositCandidate(
        VenaterraDepositCategory category,
        ResourceLocation oreId,
        ResourceLocation dropItemId,
        BlockPos center,
        float richness,
        double confidence,
        SourceMetadata source
) {
    public VenaterraDepositCandidate {
        category = category == null ? VenaterraDepositCategory.UNKNOWN_OTHER : category;
        center = center == null ? BlockPos.ZERO : center.immutable();
        source = source == null ? SourceMetadata.venaterra(null) : source;
    }

    public record SourceMetadata(String modId, String apiClass, ResourceLocation dimension) {
        static SourceMetadata venaterra(ResourceLocation dimension) {
            return new SourceMetadata(VenaterraDepositProvider.MOD_ID, VenaterraDepositProvider.API_CLASS_NAME, dimension);
        }
    }
}
