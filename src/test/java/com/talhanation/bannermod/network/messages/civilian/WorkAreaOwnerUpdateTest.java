package com.talhanation.bannermod.network.messages.civilian;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkAreaOwnerUpdateTest {

    @Test
    void rejectsMissingResolvedOwnerWithoutMutatingWorkArea() {
        UUID originalOwner = UUID.randomUUID();
        MutableArea workArea = new MutableArea(originalOwner, "original", "old-team");

        boolean updated = WorkAreaOwnerUpdate.apply(UUID.randomUUID(), null, workArea);

        assertFalse(updated);
        assertEquals(originalOwner, workArea.playerUUID);
        assertEquals("original", workArea.playerName);
        assertEquals("old-team", workArea.teamStringID);
    }

    @Test
    void rejectsSpoofedOwnerMismatchWithoutMutatingWorkArea() {
        UUID originalOwner = UUID.randomUUID();
        UUID requestedOwner = UUID.randomUUID();
        MutableArea workArea = new MutableArea(originalOwner, "original", "old-team");
        Owner resolvedOwner = new Owner(UUID.randomUUID(), "server-name", "server-team");

        boolean updated = WorkAreaOwnerUpdate.apply(requestedOwner, resolvedOwner, workArea);

        assertFalse(updated);
        assertEquals(originalOwner, workArea.playerUUID);
        assertEquals("original", workArea.playerName);
        assertEquals("old-team", workArea.teamStringID);
    }

    @Test
    void validOwnerChangeUsesResolvedNameAndTeamMetadata() {
        UUID requestedOwner = UUID.randomUUID();
        MutableArea workArea = new MutableArea(UUID.randomUUID(), "original", "old-team");
        Owner resolvedOwner = new Owner(requestedOwner, "server-name", "server-team");

        boolean updated = WorkAreaOwnerUpdate.apply(requestedOwner, resolvedOwner, workArea);

        assertTrue(updated);
        assertEquals(requestedOwner, workArea.playerUUID);
        assertEquals("server-name", workArea.playerName);
        assertEquals("server-team", workArea.teamStringID);
    }

    @Test
    void validOwnerWithoutTeamClearsStaleTeamMetadata() {
        UUID requestedOwner = UUID.randomUUID();
        MutableArea workArea = new MutableArea(UUID.randomUUID(), "original", "old-team");
        Owner resolvedOwner = new Owner(requestedOwner, "server-name", null);

        boolean updated = WorkAreaOwnerUpdate.apply(requestedOwner, resolvedOwner, workArea);

        assertTrue(updated);
        assertEquals(requestedOwner, workArea.playerUUID);
        assertEquals("server-name", workArea.playerName);
        assertEquals("", workArea.teamStringID);
    }

    private record Owner(UUID uuid, String name, String teamName) implements WorkAreaOwnerUpdate.ResolvedOwner {
    }

    private static final class MutableArea implements WorkAreaOwnerUpdate.MutableWorkArea {
        private UUID playerUUID;
        private String playerName;
        private String teamStringID;

        private MutableArea(UUID playerUUID, String playerName, String teamStringID) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.teamStringID = teamStringID;
        }

        @Override
        public void setPlayerUUID(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }

        @Override
        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        @Override
        public void setTeamStringID(String teamStringID) {
            this.teamStringID = teamStringID;
        }
    }
}
