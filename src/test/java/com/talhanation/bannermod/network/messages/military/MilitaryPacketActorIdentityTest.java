package com.talhanation.bannermod.network.messages.military;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilitaryPacketActorIdentityTest {
    @Test
    void faceCommandIgnoresSpoofedWireUuid() {
        UUID sender = UUID.randomUUID();
        UUID spoofed = UUID.randomUUID();

        assertEquals(sender, MessageFaceCommand.authorizedPlayerUuid(sender, spoofed));
    }

    @Test
    void rangedFireIgnoresSpoofedWireUuid() {
        UUID sender = UUID.randomUUID();
        UUID spoofed = UUID.randomUUID();

        assertEquals(sender, MessageRangedFire.authorizedPlayerUuid(sender, spoofed));
    }

    @Test
    void upkeepIgnoresSpoofedWireUuid() {
        UUID sender = UUID.randomUUID();
        UUID spoofed = UUID.randomUUID();

        assertEquals(sender, MessageUpkeepEntity.authorizedPlayerUuid(sender, spoofed));
    }

    @Test
    void clearUpkeepUsesSenderUuidSoForgedUuidCannotClearVictimRecruits() {
        UUID sender = UUID.randomUUID();
        UUID forgedVictim = UUID.randomUUID();

        UUID authorizedOwner = MessageClearUpkeep.authorizedPlayerUuid(sender, forgedVictim);
        assertEquals(sender, authorizedOwner);
        assertNotEquals(forgedVictim, authorizedOwner);
        assertTrue(MessageClearUpkeep.isAuthorizedOwner(sender, authorizedOwner));
        assertFalse(MessageClearUpkeep.isAuthorizedOwner(forgedVictim, authorizedOwner));
    }

    @Test
    void backToMountUsesSenderUuidSoForgedUuidCannotDispatchSiegeIntentToVictimRecruits() {
        UUID sender = UUID.randomUUID();
        UUID forgedVictim = UUID.randomUUID();

        UUID authorizedOwner = MessageBackToMountEntity.authorizedPlayerUuid(sender, forgedVictim);
        assertEquals(sender, authorizedOwner);
        assertNotEquals(forgedVictim, authorizedOwner);
        assertTrue(MessageBackToMountEntity.isAuthorizedOwner(sender, authorizedOwner));
        assertFalse(MessageBackToMountEntity.isAuthorizedOwner(forgedVictim, authorizedOwner));
    }

    @Test
    void mountUsesSenderUuidSoForgedUuidCannotDispatchSiegeIntentToVictimRecruits() {
        UUID sender = UUID.randomUUID();
        UUID forgedVictim = UUID.randomUUID();

        UUID authorizedOwner = MessageMountEntity.authorizedPlayerUuid(sender, forgedVictim);
        assertEquals(sender, authorizedOwner);
        assertNotEquals(forgedVictim, authorizedOwner);
        assertTrue(MessageMountEntity.isAuthorizedOwner(sender, authorizedOwner));
        assertFalse(MessageMountEntity.isAuthorizedOwner(forgedVictim, authorizedOwner));
    }

    @Test
    void protectUsesSenderUuidSoForgedUuidCannotOrderVictimRecruits() {
        UUID sender = UUID.randomUUID();
        UUID forgedVictim = UUID.randomUUID();

        UUID authorizedOwner = MessageProtectEntity.authorizedPlayerUuid(sender, forgedVictim);
        assertEquals(sender, authorizedOwner);
        assertNotEquals(forgedVictim, authorizedOwner);
        assertTrue(MessageProtectEntity.isAuthorizedOwner(sender, authorizedOwner));
        assertFalse(MessageProtectEntity.isAuthorizedOwner(forgedVictim, authorizedOwner));
    }
}
