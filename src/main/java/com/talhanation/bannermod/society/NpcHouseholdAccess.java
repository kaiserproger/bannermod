package com.talhanation.bannermod.society;

import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public final class NpcHouseholdAccess {
    private NpcHouseholdAccess() {
    }

    public static @Nullable UUID reconcileResidentHome(ServerLevel level,
                                                       UUID residentUuid,
                                                       @Nullable UUID homeBuildingUuid,
                                                       int residentCapacity,
                                                       long gameTime) {
        return NpcHouseholdSavedData.get(level).runtime().reconcileResidentHome(residentUuid, homeBuildingUuid, residentCapacity, gameTime);
    }

    public static void clearResident(ServerLevel level, UUID residentUuid, long gameTime) {
        NpcHouseholdSavedData.get(level).runtime().clearResident(residentUuid, gameTime);
    }

    public static void moveResident(ServerLevel level,
                                    UUID fromResidentUuid,
                                    UUID toResidentUuid,
                                    long gameTime) {
        NpcHouseholdSavedData.get(level).runtime().moveResident(fromResidentUuid, toResidentUuid, gameTime);
    }

    public static void updateHead(ServerLevel level,
                                  UUID householdId,
                                  @Nullable UUID headResidentUuid,
                                  long gameTime) {
        NpcHouseholdSavedData.get(level).runtime().updateHead(householdId, headResidentUuid, gameTime);
    }

    public static Optional<NpcHouseholdRecord> householdForResident(ServerLevel level, UUID residentUuid) {
        return NpcHouseholdSavedData.get(level).runtime().householdForResident(residentUuid);
    }

    public static Optional<NpcHouseholdRecord> householdForHome(ServerLevel level, UUID homeBuildingUuid) {
        return NpcHouseholdSavedData.get(level).runtime().householdForHome(homeBuildingUuid);
    }
}
