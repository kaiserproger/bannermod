package com.talhanation.bannermod.society;

import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.UUID;

public final class NpcHousingRequestAccess {
    private NpcHousingRequestAccess() {
    }

    public static NpcHousingRequestRecord requestHouse(ServerLevel level,
                                                       UUID residentUuid,
                                                       UUID claimUuid,
                                                       @Nullable UUID lordPlayerUuid,
                                                       long gameTime) {
        return NpcHousingRequestSavedData.get(level).runtime().ensureRequest(
                residentUuid,
                claimUuid,
                deterministicProjectId(residentUuid, claimUuid),
                lordPlayerUuid,
                gameTime
        );
    }

    public static NpcHousingRequestRecord approve(ServerLevel level, UUID residentUuid, long gameTime) {
        return NpcHousingRequestSavedData.get(level).runtime().approve(residentUuid, gameTime);
    }

    public static void markFulfilled(ServerLevel level, UUID residentUuid, long gameTime) {
        NpcHousingRequestSavedData.get(level).runtime().fulfill(residentUuid, gameTime);
    }

    public static NpcHousingRequestStatus statusFor(ServerLevel level, UUID residentUuid) {
        return NpcHousingRequestSavedData.get(level).runtime()
                .requestFor(residentUuid)
                .map(NpcHousingRequestRecord::status)
                .orElse(NpcHousingRequestStatus.NONE);
    }

    private static UUID deterministicProjectId(UUID residentUuid, UUID claimUuid) {
        long hi = residentUuid.getMostSignificantBits() ^ claimUuid.getMostSignificantBits() ^ 0x484F5553454C4FL;
        long lo = residentUuid.getLeastSignificantBits() ^ claimUuid.getLeastSignificantBits() ^ 0x52455155455354L;
        return new UUID(hi, lo);
    }
}
