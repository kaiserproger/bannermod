package com.talhanation.bannermod.entity.military;

import java.util.UUID;
import java.util.function.IntConsumer;

public final class AssassinLeaderCountAuthority {
    private AssassinLeaderCountAuthority() {
    }

    public static boolean trySetCount(UUID controlOwnerUUID, UUID senderUUID, boolean senderHasOpPermission,
                                      int count, IntConsumer countSetter) {
        if ((controlOwnerUUID != null && controlOwnerUUID.equals(senderUUID)) || senderHasOpPermission) {
            countSetter.accept(count);
            return true;
        }
        return false;
    }
}
