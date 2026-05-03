package com.talhanation.bannermod.society;

import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.UUID;

public final class NpcLivelihoodRequestAccess {
    private NpcLivelihoodRequestAccess() {
    }

    public static NpcLivelihoodRequestRecord request(ServerLevel level,
                                                     UUID claimUuid,
                                                     UUID representativeResidentUuid,
                                                     NpcLivelihoodRequestType type,
                                                     @Nullable UUID lordPlayerUuid,
                                                     long gameTime) {
        return NpcLivelihoodRequestSavedData.get(level).runtime().ensureRequest(
                claimUuid,
                representativeResidentUuid,
                type,
                deterministicProjectId(claimUuid, type),
                lordPlayerUuid,
                gameTime
        );
    }

    public static @Nullable NpcLivelihoodRequestRecord requestFor(ServerLevel level,
                                                                  UUID claimUuid,
                                                                  NpcLivelihoodRequestType type) {
        if (claimUuid == null || type == null) {
            return null;
        }
        return NpcLivelihoodRequestSavedData.get(level).runtime().requestFor(claimUuid, type).orElse(null);
    }

    public static NpcLivelihoodRequestRecord approve(ServerLevel level,
                                                     UUID claimUuid,
                                                     NpcLivelihoodRequestType type,
                                                     long gameTime) {
        return NpcLivelihoodRequestSavedData.get(level).runtime().approve(claimUuid, type, gameTime);
    }

    public static NpcLivelihoodRequestRecord deny(ServerLevel level,
                                                  UUID claimUuid,
                                                  NpcLivelihoodRequestType type,
                                                  long gameTime) {
        return NpcLivelihoodRequestSavedData.get(level).runtime().deny(claimUuid, type, gameTime);
    }

    public static void fulfill(ServerLevel level,
                               UUID claimUuid,
                               NpcLivelihoodRequestType type,
                               long gameTime) {
        NpcLivelihoodRequestSavedData.get(level).runtime().fulfill(claimUuid, type, gameTime);
    }

    private static UUID deterministicProjectId(UUID claimUuid, NpcLivelihoodRequestType type) {
        long hi = claimUuid.getMostSignificantBits() ^ (0x4C4956454C49484FL + type.ordinal());
        long lo = claimUuid.getLeastSignificantBits() ^ (0x4F4F4450524A545L + (type.ordinal() * 31L));
        return new UUID(hi, lo);
    }
}
