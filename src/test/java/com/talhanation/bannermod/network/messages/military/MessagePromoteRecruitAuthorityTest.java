package com.talhanation.bannermod.network.messages.military;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagePromoteRecruitAuthorityTest {
    @Test
    void foreignNearbySenderCannotReachPromotion() throws IOException {
        Path handler = Paths.get("src/main/java/com/talhanation/bannermod/network/messages/military/MessagePromoteRecruit.java");
        String source = Files.readString(handler);

        int authorityCheck = source.indexOf("RecruitCommandAuthority.canDirectlyControl(sender, recruit)");
        int promoteRecruit = source.indexOf("RecruitEvents.promoteRecruit(recruit, profession, name, sender)");

        assertTrue(authorityCheck >= 0, "promote recruit must require direct recruit command authority");
        assertTrue(promoteRecruit >= 0, "promote recruit must still promote authorized recruits");
        assertTrue(authorityCheck < promoteRecruit,
                "a forged recruit UUID must hit the authority check before promotion can discard or transfer it");
    }
}
