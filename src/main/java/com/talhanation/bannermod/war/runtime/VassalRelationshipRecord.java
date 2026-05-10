package com.talhanation.bannermod.war.runtime;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record VassalRelationshipRecord(
        UUID id,
        UUID overlordEntityId,
        UUID vassalEntityId,
        String obligations,
        int tributeAmount,
        long tributeIntervalTicks,
        UUID sourceWarId,
        long createdAtGameTime,
        boolean active
) {
    public VassalRelationshipRecord {
        obligations = obligations == null ? "" : obligations;
        tributeAmount = Math.max(0, tributeAmount);
        tributeIntervalTicks = Math.max(0L, tributeIntervalTicks);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("OverlordEntityId", overlordEntityId);
        tag.putUUID("VassalEntityId", vassalEntityId);
        tag.putString("Obligations", obligations);
        tag.putInt("TributeAmount", tributeAmount);
        tag.putLong("TributeIntervalTicks", tributeIntervalTicks);
        if (sourceWarId != null) {
            tag.putUUID("SourceWarId", sourceWarId);
        }
        tag.putLong("CreatedAtGameTime", createdAtGameTime);
        tag.putBoolean("Active", active);
        return tag;
    }

    public static VassalRelationshipRecord fromTag(CompoundTag tag) {
        return new VassalRelationshipRecord(
                tag.getUUID("Id"),
                tag.getUUID("OverlordEntityId"),
                tag.getUUID("VassalEntityId"),
                tag.getString("Obligations"),
                tag.getInt("TributeAmount"),
                tag.getLong("TributeIntervalTicks"),
                tag.hasUUID("SourceWarId") ? tag.getUUID("SourceWarId") : null,
                tag.getLong("CreatedAtGameTime"),
                !tag.contains("Active") || tag.getBoolean("Active")
        );
    }
}
