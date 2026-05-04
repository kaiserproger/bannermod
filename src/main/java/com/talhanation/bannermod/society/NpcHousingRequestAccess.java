package com.talhanation.bannermod.society;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.UUID;

public final class NpcHousingRequestAccess {
    private NpcHousingRequestAccess() {
    }

    public static NpcHousingRequestRecord requestHouse(ServerLevel level,
                                                       UUID householdId,
                                                       UUID residentUuid,
                                                       UUID claimUuid,
                                                       @Nullable UUID lordPlayerUuid,
                                                       long gameTime) {
        return NpcHousingRequestSavedData.get(level).runtime().ensureRequest(
                householdId,
                residentUuid,
                claimUuid,
                deterministicProjectId(householdId, claimUuid),
                lordPlayerUuid,
                gameTime
        );
    }

    public static NpcHousingRequestRecord approve(ServerLevel level, UUID residentUuid, long gameTime) {
        UUID householdId = householdIdFor(level, residentUuid);
        if (householdId == null) {
            throw new IllegalArgumentException("No household exists for resident " + residentUuid);
        }
        return NpcHousingRequestSavedData.get(level).runtime().approve(householdId, gameTime);
    }

    public static NpcHousingRequestRecord approveHousehold(ServerLevel level, UUID householdId, long gameTime) {
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        return NpcHousingRequestSavedData.get(level).runtime().approve(householdId, gameTime);
    }

    public static NpcHousingRequestRecord denyHousehold(ServerLevel level, UUID householdId, long gameTime) {
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        return NpcHousingRequestSavedData.get(level).runtime().deny(householdId, gameTime);
    }

    public static NpcHousingRequestRecord reservePlot(ServerLevel level,
                                                      UUID householdId,
                                                      @Nullable BlockPos reservedPlotPos,
                                                      long gameTime) {
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        return NpcHousingRequestSavedData.get(level).runtime().reservePlot(householdId, reservedPlotPos, gameTime);
    }

    public static NpcHousingRequestRecord bindBuildArea(ServerLevel level,
                                                        UUID householdId,
                                                        @Nullable UUID buildAreaUuid,
                                                        long gameTime) {
        if (householdId == null) {
            throw new IllegalArgumentException("householdId must not be null");
        }
        return NpcHousingRequestSavedData.get(level).runtime().bindBuildArea(householdId, buildAreaUuid, gameTime);
    }

    public static @Nullable NpcHousingRequestRecord requestForHousehold(ServerLevel level, UUID householdId) {
        if (householdId == null) {
            return null;
        }
        return NpcHousingRequestSavedData.get(level).runtime().requestForHousehold(householdId).orElse(null);
    }

    public static @Nullable NpcHousingRequestRecord requestForProject(ServerLevel level, UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return NpcHousingRequestSavedData.get(level).runtime().requestForProject(projectId).orElse(null);
    }

    public static void markFulfilled(ServerLevel level, UUID residentUuid, long gameTime) {
        NpcHouseholdRecord household = NpcHouseholdAccess.householdForResident(level, residentUuid).orElse(null);
        if (household == null || household.housingState() != NpcHouseholdHousingState.NORMAL) {
            return;
        }
        NpcHousingRequestSavedData.get(level).runtime().fulfill(household.householdId(), gameTime);
    }

    public static NpcHousingRequestStatus statusFor(ServerLevel level, UUID residentUuid) {
        UUID householdId = householdIdFor(level, residentUuid);
        if (householdId == null) {
            return NpcHousingRequestStatus.NONE;
        }
        return NpcHousingRequestSavedData.get(level).runtime()
                .requestForHousehold(householdId)
                .map(NpcHousingRequestRecord::status)
                .orElse(NpcHousingRequestStatus.NONE);
    }

    private static @Nullable UUID householdIdFor(ServerLevel level, UUID residentUuid) {
        return NpcHouseholdAccess.householdForResident(level, residentUuid)
                .map(NpcHouseholdRecord::householdId)
                .orElse(null);
    }

    private static UUID deterministicProjectId(UUID householdId, UUID claimUuid) {
        long hi = householdId.getMostSignificantBits() ^ claimUuid.getMostSignificantBits() ^ 0x484F5553454C4FL;
        long lo = householdId.getLeastSignificantBits() ^ claimUuid.getLeastSignificantBits() ^ 0x52455155455354L;
        return new UUID(hi, lo);
    }
}
