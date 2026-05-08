package com.talhanation.bannermod.network.messages.military;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageHireAuthorityTest {
    private static final Path HANDLER = Path.of(
            "src/main/java/com/talhanation/bannermod/network/messages/military/MessageHire.java");

    @Test
    void forgedForeignGroupUuidCannotReachHireHandling() throws IOException {
        String src = Files.readString(HANDLER);
        String authorityGate = "RecruitCommandAuthority.ownedGroup(player, groupUUID)";
        String unsafeLookup = "RecruitEvents.groupsManager().getGroup(groupUUID)";
        String hireHandling = "CommandEvents.handleRecruiting(player, group, recruit, true)";

        int gateIndex = src.indexOf(authorityGate);
        int hireHandlingIndex = src.indexOf(hireHandling);

        assertTrue(gateIndex >= 0, "hire must resolve the requested group through the canonical ownership gate");
        assertEquals(-1, src.indexOf(unsafeLookup), "hire must not pass arbitrary wire group UUIDs to group lookup");
        assertTrue(hireHandlingIndex >= 0, "authorized hires must still reach recruit hire handling");
        assertTrue(gateIndex < hireHandlingIndex,
                "foreign group UUIDs must become null before hire handling can add members or update ownership");
    }
}
