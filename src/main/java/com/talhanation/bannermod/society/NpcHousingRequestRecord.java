package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record NpcHousingRequestRecord(
        UUID householdId,
        UUID residentUuid,
        UUID claimUuid,
        UUID projectId,
        @Nullable UUID lordPlayerUuid,
        NpcHousingRequestStatus status,
        long requestedAtGameTime,
        long updatedAtGameTime
) {
    public NpcHousingRequestRecord {
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        if (residentUuid == null) {
            throw new IllegalArgumentException("residentUuid must not be null");
        }
        if (claimUuid == null) {
            throw new IllegalArgumentException("claimUuid must not be null");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
        if (status == null) {
            status = NpcHousingRequestStatus.NONE;
        }
    }

    public static NpcHousingRequestRecord create(UUID householdId,
                                                 UUID residentUuid,
                                                 UUID claimUuid,
                                                 UUID projectId,
                                                 @Nullable UUID lordPlayerUuid,
                                                 long gameTime) {
        return new NpcHousingRequestRecord(
                householdId,
                residentUuid,
                claimUuid,
                projectId,
                lordPlayerUuid,
                NpcHousingRequestStatus.REQUESTED,
                gameTime,
                gameTime
        );
    }

    public NpcHousingRequestRecord approve(long gameTime) {
        if (this.status == NpcHousingRequestStatus.APPROVED) {
            return this;
        }
        return new NpcHousingRequestRecord(
                this.householdId,
                this.residentUuid,
                this.claimUuid,
                this.projectId,
                this.lordPlayerUuid,
                NpcHousingRequestStatus.APPROVED,
                this.requestedAtGameTime,
                gameTime
        );
    }

    public NpcHousingRequestRecord deny(long gameTime) {
        if (this.status == NpcHousingRequestStatus.DENIED) {
            return this;
        }
        return new NpcHousingRequestRecord(
                this.householdId,
                this.residentUuid,
                this.claimUuid,
                this.projectId,
                this.lordPlayerUuid,
                NpcHousingRequestStatus.DENIED,
                this.requestedAtGameTime,
                gameTime
        );
    }

    public NpcHousingRequestRecord fulfill(long gameTime) {
        if (this.status == NpcHousingRequestStatus.FULFILLED) {
            return this;
        }
        return new NpcHousingRequestRecord(
                this.householdId,
                this.residentUuid,
                this.claimUuid,
                this.projectId,
                this.lordPlayerUuid,
                NpcHousingRequestStatus.FULFILLED,
                this.requestedAtGameTime,
                gameTime
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("HouseholdId", this.householdId);
        tag.putUUID("ResidentUuid", this.residentUuid);
        tag.putUUID("ClaimUuid", this.claimUuid);
        tag.putUUID("ProjectId", this.projectId);
        if (this.lordPlayerUuid != null) {
            tag.putUUID("LordPlayerUuid", this.lordPlayerUuid);
        }
        tag.putString("Status", this.status.name());
        tag.putLong("RequestedAt", this.requestedAtGameTime);
        tag.putLong("UpdatedAt", this.updatedAtGameTime);
        return tag;
    }

    public static NpcHousingRequestRecord fromTag(CompoundTag tag) {
        UUID residentUuid = tag.getUUID("ResidentUuid");
        UUID householdId = tag.contains("HouseholdId") ? tag.getUUID("HouseholdId") : residentUuid;
        return new NpcHousingRequestRecord(
                householdId,
                residentUuid,
                tag.getUUID("ClaimUuid"),
                tag.getUUID("ProjectId"),
                tag.contains("LordPlayerUuid") ? tag.getUUID("LordPlayerUuid") : null,
                NpcHousingRequestStatus.fromName(tag.getString("Status")),
                tag.getLong("RequestedAt"),
                tag.getLong("UpdatedAt")
        );
    }
}
