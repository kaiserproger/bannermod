package com.talhanation.bannermod.war.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarAllyRuntimeTest {

    @Test
    void appendAllyPersistsToCorrectSideAndDirtiesOnce() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        AtomicInteger dirty = new AtomicInteger();
        runtime.setDirtyListener(dirty::incrementAndGet);

        WarDeclarationRecord war = runtime.declareWar(
                UUID.randomUUID(), UUID.randomUUID(),
                WarGoalType.WHITE_PEACE, "", java.util.List.of(),
                java.util.List.of(), java.util.List.of(),
                0L, 0L
        ).orElseThrow();
        UUID warId = war.id();
        UUID ally = UUID.randomUUID();

        var updated = runtime.appendAlly(warId, WarSide.DEFENDER, ally).orElseThrow();
        assertEquals(java.util.List.of(ally), updated.defenderAllyIds());
        assertTrue(updated.attackerAllyIds().isEmpty());

        // Idempotent: re-appending same ally should not dirty again.
        int afterFirst = dirty.get();
        runtime.appendAlly(warId, WarSide.DEFENDER, ally);
        assertEquals(afterFirst, dirty.get());
    }

    @Test
    void removeAllyDropsEntry() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        WarDeclarationRecord war = runtime.declareWar(
                UUID.randomUUID(), UUID.randomUUID(),
                WarGoalType.WHITE_PEACE, "", java.util.List.of(),
                java.util.List.of(), java.util.List.of(),
                0L, 0L
        ).orElseThrow();
        UUID ally = UUID.randomUUID();
        runtime.appendAlly(war.id(), WarSide.ATTACKER, ally);

        var removed = runtime.removeAlly(war.id(), WarSide.ATTACKER, ally).orElseThrow();
        assertTrue(removed.attackerAllyIds().isEmpty());
    }

    @Test
    void inviteRuntimeRoundTripsThroughNbt() {
        WarAllyInviteRuntime runtime = new WarAllyInviteRuntime();
        UUID warId = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();
        WarAllyInviteRecord record = runtime.create(warId, WarSide.DEFENDER, invitee, inviter, 42L);

        WarAllyInviteRuntime decoded = WarAllyInviteRuntime.fromTag(runtime.toTag());

        assertEquals(1, decoded.all().size());
        WarAllyInviteRecord roundTripped = decoded.all().iterator().next();
        assertEquals(record.id(), roundTripped.id());
        assertEquals(record.warId(), roundTripped.warId());
        assertEquals(WarSide.DEFENDER, roundTripped.side());
        assertEquals(invitee, roundTripped.inviteePoliticalEntityId());
        assertEquals(inviter, roundTripped.inviterPlayerUuid());
        assertEquals(42L, roundTripped.createdAtGameTime());
    }

    @Test
    void inviteRuntimeFindsExistingDuplicate() {
        WarAllyInviteRuntime runtime = new WarAllyInviteRuntime();
        UUID warId = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        WarAllyInviteRecord first = runtime.create(warId, WarSide.ATTACKER, invitee, null, 0L);
        WarAllyInviteRecord again = runtime.existing(warId, WarSide.ATTACKER, invitee).orElseThrow();
        assertSame(first, again);
        assertNull(runtime.existing(warId, WarSide.DEFENDER, invitee).orElse(null));
    }

    @Test
    void inviteRemoveForWarClearsEverythingForThatWar() {
        WarAllyInviteRuntime runtime = new WarAllyInviteRuntime();
        UUID war1 = UUID.randomUUID();
        UUID war2 = UUID.randomUUID();
        runtime.create(war1, WarSide.ATTACKER, UUID.randomUUID(), null, 0L);
        runtime.create(war1, WarSide.DEFENDER, UUID.randomUUID(), null, 0L);
        runtime.create(war2, WarSide.ATTACKER, UUID.randomUUID(), null, 0L);

        int removed = runtime.removeForWar(war1);
        assertEquals(2, removed);
        assertEquals(1, runtime.all().size());
        assertNotNull(runtime.all().iterator().next());
    }

    @Test
    void warSideParseAcceptsCaseInsensitive() {
        assertEquals(WarSide.ATTACKER, WarSide.parse("attacker"));
        assertEquals(WarSide.DEFENDER, WarSide.parse("Defender"));
        assertNull(WarSide.parse("nonsense"));
        assertNull(WarSide.parse(null));
    }

    @Test
    void appendAllyRejectsMissingArgs() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        assertFalse(runtime.appendAlly(null, WarSide.ATTACKER, UUID.randomUUID()).isPresent());
        assertFalse(runtime.appendAlly(UUID.randomUUID(), null, UUID.randomUUID()).isPresent());
        assertFalse(runtime.appendAlly(UUID.randomUUID(), WarSide.ATTACKER, null).isPresent());
    }
}
