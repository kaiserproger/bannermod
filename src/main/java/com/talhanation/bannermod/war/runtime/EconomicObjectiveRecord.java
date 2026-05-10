package com.talhanation.bannermod.war.runtime;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record EconomicObjectiveRecord(
        UUID id,
        EconomicObjectiveType type,
        EconomicObjectiveTargetKind targetKind,
        @Nullable UUID warId,
        @Nullable UUID localHostilityId,
        UUID claimUuid,
        @Nullable UUID strategicObjectId,
        long createdGameTime,
        long expiresGameTime,
        long resolvedGameTime
) {
    public EconomicObjectiveRecord {
        id = id == null ? UUID.randomUUID() : id;
        type = type == null ? EconomicObjectiveType.MINE_DISPUTE : type;
        targetKind = targetKind == null ? EconomicObjectiveTargetKind.MINE : targetKind;
        claimUuid = claimUuid == null ? new UUID(0L, 0L) : claimUuid;
        createdGameTime = Math.max(0L, createdGameTime);
        expiresGameTime = Math.max(0L, expiresGameTime);
        resolvedGameTime = Math.max(0L, resolvedGameTime);
    }

    public boolean isActiveAt(long gameTime) {
        return gameTime >= createdGameTime && resolvedGameTime <= 0L && (expiresGameTime <= 0L || gameTime < expiresGameTime);
    }

    public EconomicObjectiveRecord resolve(long gameTime) {
        return new EconomicObjectiveRecord(
                id,
                type,
                targetKind,
                warId,
                localHostilityId,
                claimUuid,
                strategicObjectId,
                createdGameTime,
                expiresGameTime,
                Math.max(1L, gameTime)
        );
    }

    public EconomicObjectiveState economyStateAt(long gameTime) {
        return isActiveAt(gameTime) ? type.economyState() : EconomicObjectiveState.NORMAL;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.name());
        tag.putString("TargetKind", targetKind.name());
        if (warId != null) {
            tag.putUUID("WarId", warId);
        }
        if (localHostilityId != null) {
            tag.putUUID("LocalHostilityId", localHostilityId);
        }
        tag.putUUID("ClaimUuid", claimUuid);
        if (strategicObjectId != null) {
            tag.putUUID("StrategicObjectId", strategicObjectId);
        }
        tag.putLong("CreatedGameTime", createdGameTime);
        tag.putLong("ExpiresGameTime", expiresGameTime);
        tag.putLong("ResolvedGameTime", resolvedGameTime);
        return tag;
    }

    public static EconomicObjectiveRecord fromTag(CompoundTag tag) {
        return new EconomicObjectiveRecord(
                tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(),
                enumValue(EconomicObjectiveType.class, tag.getString("Type"), EconomicObjectiveType.MINE_DISPUTE),
                enumValue(EconomicObjectiveTargetKind.class, tag.getString("TargetKind"), EconomicObjectiveTargetKind.MINE),
                tag.hasUUID("WarId") ? tag.getUUID("WarId") : null,
                tag.hasUUID("LocalHostilityId") ? tag.getUUID("LocalHostilityId") : null,
                tag.hasUUID("ClaimUuid") ? tag.getUUID("ClaimUuid") : new UUID(0L, 0L),
                tag.hasUUID("StrategicObjectId") ? tag.getUUID("StrategicObjectId") : null,
                tag.getLong("CreatedGameTime"),
                tag.getLong("ExpiresGameTime"),
                tag.getLong("ResolvedGameTime")
        );
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
