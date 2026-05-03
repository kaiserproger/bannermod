package com.talhanation.bannermod.society;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record NpcLivelihoodRequestRecord(
        UUID claimUuid,
        UUID representativeResidentUuid,
        NpcLivelihoodRequestType type,
        UUID projectId,
        @Nullable UUID lordPlayerUuid,
        NpcLivelihoodRequestStatus status,
        long requestedAtGameTime,
        long updatedAtGameTime
) {
    public NpcLivelihoodRequestRecord {
        if (claimUuid == null) {
            throw new IllegalArgumentException("claimUuid must not be null");
        }
        if (representativeResidentUuid == null) {
            throw new IllegalArgumentException("representativeResidentUuid must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
        if (status == null) {
            status = NpcLivelihoodRequestStatus.NONE;
        }
    }

    public static NpcLivelihoodRequestRecord create(UUID claimUuid,
                                                    UUID representativeResidentUuid,
                                                    NpcLivelihoodRequestType type,
                                                    UUID projectId,
                                                    @Nullable UUID lordPlayerUuid,
                                                    long gameTime) {
        return new NpcLivelihoodRequestRecord(
                claimUuid,
                representativeResidentUuid,
                type,
                projectId,
                lordPlayerUuid,
                NpcLivelihoodRequestStatus.REQUESTED,
                gameTime,
                gameTime
        );
    }

    public NpcLivelihoodRequestRecord approve(long gameTime) {
        if (this.status == NpcLivelihoodRequestStatus.APPROVED) {
            return this;
        }
        return new NpcLivelihoodRequestRecord(
                this.claimUuid,
                this.representativeResidentUuid,
                this.type,
                this.projectId,
                this.lordPlayerUuid,
                NpcLivelihoodRequestStatus.APPROVED,
                this.requestedAtGameTime,
                gameTime
        );
    }

    public NpcLivelihoodRequestRecord deny(long gameTime) {
        if (this.status == NpcLivelihoodRequestStatus.DENIED) {
            return this;
        }
        return new NpcLivelihoodRequestRecord(
                this.claimUuid,
                this.representativeResidentUuid,
                this.type,
                this.projectId,
                this.lordPlayerUuid,
                NpcLivelihoodRequestStatus.DENIED,
                this.requestedAtGameTime,
                gameTime
        );
    }

    public NpcLivelihoodRequestRecord fulfill(long gameTime) {
        if (this.status == NpcLivelihoodRequestStatus.FULFILLED) {
            return this;
        }
        return new NpcLivelihoodRequestRecord(
                this.claimUuid,
                this.representativeResidentUuid,
                this.type,
                this.projectId,
                this.lordPlayerUuid,
                NpcLivelihoodRequestStatus.FULFILLED,
                this.requestedAtGameTime,
                gameTime
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ClaimUuid", this.claimUuid);
        tag.putUUID("RepresentativeResidentUuid", this.representativeResidentUuid);
        tag.putString("Type", this.type.name());
        tag.putUUID("ProjectId", this.projectId);
        if (this.lordPlayerUuid != null) {
            tag.putUUID("LordPlayerUuid", this.lordPlayerUuid);
        }
        tag.putString("Status", this.status.name());
        tag.putLong("RequestedAt", this.requestedAtGameTime);
        tag.putLong("UpdatedAt", this.updatedAtGameTime);
        return tag;
    }

    public static NpcLivelihoodRequestRecord fromTag(CompoundTag tag) {
        NpcLivelihoodRequestType type = NpcLivelihoodRequestType.fromName(tag.getString("Type"));
        if (type == null) {
            type = NpcLivelihoodRequestType.LUMBER_CAMP;
        }
        return new NpcLivelihoodRequestRecord(
                tag.getUUID("ClaimUuid"),
                tag.getUUID("RepresentativeResidentUuid"),
                type,
                tag.getUUID("ProjectId"),
                tag.contains("LordPlayerUuid") ? tag.getUUID("LordPlayerUuid") : null,
                NpcLivelihoodRequestStatus.fromName(tag.getString("Status")),
                tag.getLong("RequestedAt"),
                tag.getLong("UpdatedAt")
        );
    }
}
