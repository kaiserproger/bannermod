package com.talhanation.bannermod.network.messages.civilian;

import java.util.UUID;

final class WorkAreaOwnerUpdate {

    private WorkAreaOwnerUpdate() {
    }

    static boolean apply(UUID requestedOwnerUuid, ResolvedOwner resolvedOwner, MutableWorkArea workArea) {
        if (requestedOwnerUuid == null || resolvedOwner == null || !requestedOwnerUuid.equals(resolvedOwner.uuid())) {
            return false;
        }

        workArea.setPlayerUUID(resolvedOwner.uuid());
        workArea.setPlayerName(resolvedOwner.name());
        workArea.setTeamStringID(resolvedOwner.teamName() == null ? "" : resolvedOwner.teamName());
        return true;
    }

    interface ResolvedOwner {
        UUID uuid();

        String name();

        String teamName();
    }

    interface MutableWorkArea {
        void setPlayerUUID(UUID playerUUID);

        void setPlayerName(String playerName);

        void setTeamStringID(String teamStringID);
    }
}
