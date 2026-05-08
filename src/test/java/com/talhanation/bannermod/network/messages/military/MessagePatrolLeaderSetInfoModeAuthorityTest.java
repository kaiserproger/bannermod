package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.army.command.CommandHierarchy;
import com.talhanation.bannermod.army.command.CommandRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagePatrolLeaderSetInfoModeAuthorityTest {
    private static final Path HANDLER = Path.of(
            "src/main/java/com/talhanation/bannermod/network/messages/military/MessagePatrolLeaderSetInfoMode.java");

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000913");
    private static final UUID FOREIGN_SENDER = UUID.fromString("00000000-0000-0000-0000-000000000914");

    @Test
    void forgedForeignLeaderInfoModePacketCannotReachMutation() throws IOException {
        assertEquals(CommandRole.NONE,
                CommandHierarchy.roleFor(FOREIGN_SENDER, null, false, OWNER, null, true),
                "Foreign non-op sender must not directly control another player's leader");

        String src = Files.readString(HANDLER);
        String authorityGate = "RecruitCommandAuthority.canDirectlyControl(player, leader)";
        String handlerMutation = "leader.setInfoMode(state)";

        int gateIndex = src.indexOf(authorityGate);
        int mutationIndex = src.indexOf(handlerMutation);

        assertTrue(gateIndex >= 0, "Info-mode handler must use the canonical recruit authority gate");
        assertTrue(mutationIndex >= 0, "Info-mode handler must still mutate info mode for authorized leaders");
        assertTrue(gateIndex < mutationIndex,
                "Forged packet for a foreign nearby leader must fail authority before info-mode state can change");
        assertEquals(mutationIndex, src.lastIndexOf(handlerMutation),
                "The guarded handler path must be the only info-mode mutation entry point");
    }
}
