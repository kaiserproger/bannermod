package com.talhanation.bannermod.network.messages.military;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSendMessengerAuthorityTest {
    @Test
    void foreignNearbySenderCannotMutateMessengerDelivery() throws IOException {
        Path handler = Paths.get("src/main/java/com/talhanation/bannermod/network/messages/military/MessageSendMessenger.java");
        String source = Files.readString(handler);

        int authorityCheck = source.indexOf("!player.getUUID().equals(messenger.getOwnerUUID()) && !player.hasPermissions(2)");
        int setMessage = source.indexOf("messenger.setMessage(this.message)");
        int setTargetPlayerInfo = source.indexOf("messenger.setTargetPlayerInfo");
        int setTreatyState = source.indexOf("messenger.setIsTreatyMessenger(false)");
        int startDelivery = source.indexOf("messenger.start()");

        assertTrue(authorityCheck >= 0, "messenger send must require owner or op authority");
        assertTrue(setMessage >= 0, "messenger send must still set message for authorized senders");
        assertTrue(setTargetPlayerInfo >= 0, "messenger send must still set target for authorized senders");
        assertTrue(setTreatyState >= 0, "messenger send must still reset treaty state for authorized senders");
        assertTrue(startDelivery >= 0, "messenger send must still start delivery for authorized senders");
        assertTrue(authorityCheck < setMessage, "foreign senders must be rejected before message mutation");
        assertTrue(authorityCheck < setTargetPlayerInfo, "foreign senders must be rejected before target mutation");
        assertTrue(authorityCheck < setTreatyState, "foreign senders must be rejected before treaty state mutation");
        assertTrue(authorityCheck < startDelivery, "foreign senders must be rejected before delivery start");
    }
}
