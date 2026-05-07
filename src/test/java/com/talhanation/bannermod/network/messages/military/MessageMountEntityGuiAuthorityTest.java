package com.talhanation.bannermod.network.messages.military;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageMountEntityGuiAuthorityTest {
    @Test
    void forgedRecruitUuidCannotDispatchSiegeMachineIntentForForeignRecruit() throws IOException {
        Path handler = Paths.get("src/main/java/com/talhanation/bannermod/network/messages/military/MessageMountEntityGui.java");
        String source = Files.readString(handler);

        int authorityCheck = source.indexOf("RecruitCommandAuthority.canDirectlyControl(player, recruit)");
        int siegeIntent = source.indexOf("new CommandIntent.SiegeMachine");

        assertTrue(authorityCheck >= 0, "mount GUI must check direct recruit command authority");
        assertTrue(siegeIntent >= 0, "mount GUI must still dispatch SiegeMachine intents for authorized recruits");
        assertTrue(authorityCheck < siegeIntent,
                "a forged recruit UUID must hit the authority check before any SiegeMachine intent can dispatch");
    }
}
