package com.talhanation.bannermod.society;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record NpcHousingRequestRecord(
        UUID householdId,
        UUID residentUuid,
        UUID claimUuid,
        UUID projectId,
        @Nullable UUID lordPlayerUuid,
        @Nullable BlockPos reservedPlotPos,
        @Nullable UUID buildAreaUuid,
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
                null,
                null,
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
                this.reservedPlotPos,
                this.buildAreaUuid,
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
                this.reservedPlotPos,
                this.buildAreaUuid,
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
                this.reservedPlotPos,
                this.buildAreaUuid,
                NpcHousingRequestStatus.FULFILLED,
                this.requestedAtGameTime,
                gameTime
        );
    }

    public NpcHousingRequestRecord reservePlot(@Nullable BlockPos reservedPlotPos, long gameTime) {
        if (sameBlockPos(this.reservedPlotPos, reservedPlotPos)) {
            return this;
        }
        return new NpcHousingRequestRecord(
                this.householdId,
                this.residentUuid,
                this.claimUuid,
                this.projectId,
                this.lordPlayerUuid,
                reservedPlotPos,
                this.buildAreaUuid,
                this.status,
                this.requestedAtGameTime,
                gameTime
        );
    }

    public NpcHousingRequestRecord bindBuildArea(@Nullable UUID buildAreaUuid, long gameTime) {
        if (sameNullableUuid(this.buildAreaUuid, buildAreaUuid)) {
            return this;
        }
        return new NpcHousingRequestRecord(
                this.householdId,
                this.residentUuid,
                this.claimUuid,
                this.projectId,
                this.lordPlayerUuid,
                this.reservedPlotPos,
                buildAreaUuid,
                this.status,
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
        if (this.reservedPlotPos != null) {
            tag.putLong("ReservedPlotPos", this.reservedPlotPos.asLong());
        }
        if (this.buildAreaUuid != null) {
            tag.putUUID("BuildAreaUuid", this.buildAreaUuid);
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
                tag.contains("ReservedPlotPos") ? BlockPos.of(tag.getLong("ReservedPlotPos")) : null,
                tag.contains("BuildAreaUuid") ? tag.getUUID("BuildAreaUuid") : null,
                NpcHousingRequestStatus.fromName(tag.getString("Status")),
                tag.getLong("RequestedAt"),
                tag.getLong("UpdatedAt")
        );
    }

    private static boolean sameNullableUuid(@Nullable UUID left, @Nullable UUID right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean sameBlockPos(@Nullable BlockPos left, @Nullable BlockPos right) {
        return left == null ? right == null : left.equals(right);
    }
}
