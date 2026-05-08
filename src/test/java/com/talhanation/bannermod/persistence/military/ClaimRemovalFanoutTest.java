package com.talhanation.bannermod.persistence.military;

import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import com.talhanation.bannermod.war.runtime.OccupationRuntime;
import com.talhanation.bannermod.war.runtime.RevoltRuntime;
import com.talhanation.bannermod.war.runtime.RevoltState;
import org.junit.jupiter.api.Test;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRemovalFanoutTest {

    @Test
    void fanoutClearsTreasurySettlementGovernorOccupationsAndRevoltsForTargetClaim() {
        UUID claimUuid = UUID.randomUUID();
        UUID otherClaimUuid = UUID.randomUUID();
        ChunkPos anchorChunk = new ChunkPos(4, 7);
        List<ChunkPos> claimChunks = List.of(anchorChunk, new ChunkPos(4, 8));
        List<ChunkPos> otherChunks = List.of(new ChunkPos(50, 50));

        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        treasury.depositTaxes(claimUuid, anchorChunk, "blueguild", 30, 100L);
        treasury.depositTaxes(otherClaimUuid, new ChunkPos(50, 50), "redguild", 12, 100L);

        SettlementManager settlements = new SettlementManager();
        settlements.putSnapshot(SettlementSnapshot.create(claimUuid, anchorChunk, "blueguild"));
        settlements.putSnapshot(SettlementSnapshot.create(otherClaimUuid, new ChunkPos(50, 50), "redguild"));

        BannerModGovernorManager governors = new BannerModGovernorManager();
        governors.putSnapshot(BannerModGovernorSnapshot.create(claimUuid, anchorChunk, "blueguild")
                .withGovernor(UUID.randomUUID(), UUID.randomUUID()));
        governors.putSnapshot(BannerModGovernorSnapshot.create(otherClaimUuid, new ChunkPos(50, 50), "redguild")
                .withGovernor(UUID.randomUUID(), UUID.randomUUID()));

        UUID warId = UUID.randomUUID();
        UUID attackerEntityId = UUID.randomUUID();
        UUID defenderEntityId = UUID.randomUUID();
        OccupationRuntime occupations = new OccupationRuntime();
        UUID targetOccupationId = occupations.place(warId, attackerEntityId, defenderEntityId, claimChunks, 100L)
                .orElseThrow().id();
        UUID otherOccupationId = occupations.place(warId, attackerEntityId, defenderEntityId, otherChunks, 100L)
                .orElseThrow().id();

        RevoltRuntime revolts = new RevoltRuntime();
        UUID rebelId = UUID.randomUUID();
        UUID targetRevoltId = revolts.schedule(targetOccupationId, rebelId, attackerEntityId, 110L)
                .orElseThrow().id();
        UUID otherRevoltId = revolts.schedule(otherOccupationId, rebelId, attackerEntityId, 110L)
                .orElseThrow().id();

        ClaimRemovalFanout.FanoutResult result = ClaimRemovalFanout.apply(
                claimUuid, claimChunks, treasury, settlements, governors, occupations, revolts, null);

        assertTrue(result.treasuryLedgerRemoved(), "treasury ledger should be removed");
        assertTrue(result.settlementSnapshotRemoved(), "settlement snapshot should be removed");
        assertTrue(result.governorSnapshotRemoved(), "governor snapshot should be removed");
        assertEquals(List.of(targetOccupationId), result.removedOccupationIds());
        assertEquals(List.of(targetRevoltId), result.removedRevoltIds());

        // Target claim wiped
        assertNull(treasury.getLedger(claimUuid));
        assertNull(settlements.getSnapshot(claimUuid));
        assertNull(governors.getSnapshot(claimUuid));
        assertTrue(occupations.byId(targetOccupationId).isEmpty());
        assertTrue(revolts.byId(targetRevoltId).isEmpty());

        // Unrelated claim untouched (parity)
        BannerModTreasuryLedgerSnapshot otherLedger = treasury.getLedger(otherClaimUuid);
        assertNotNull(otherLedger);
        assertEquals(12, otherLedger.accruedTaxes());
        assertNotNull(settlements.getSnapshot(otherClaimUuid));
        assertNotNull(governors.getSnapshot(otherClaimUuid));
        assertTrue(occupations.byId(otherOccupationId).isPresent());
        Optional<com.talhanation.bannermod.war.runtime.RevoltRecord> otherRevolt = revolts.byId(otherRevoltId);
        assertTrue(otherRevolt.isPresent());
        assertEquals(RevoltState.PENDING, otherRevolt.get().state());
    }

    @Test
    void fanoutIsIdempotent() {
        UUID claimUuid = UUID.randomUUID();
        ChunkPos chunk = new ChunkPos(1, 1);
        List<ChunkPos> chunks = List.of(chunk);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        treasury.depositTaxes(claimUuid, chunk, "blueguild", 5, 50L);
        SettlementManager settlements = new SettlementManager();
        settlements.putSnapshot(SettlementSnapshot.create(claimUuid, chunk, "blueguild"));
        BannerModGovernorManager governors = new BannerModGovernorManager();
        OccupationRuntime occupations = new OccupationRuntime();
        RevoltRuntime revolts = new RevoltRuntime();

        ClaimRemovalFanout.FanoutResult first = ClaimRemovalFanout.apply(
                claimUuid, chunks, treasury, settlements, governors, occupations, revolts, null);
        ClaimRemovalFanout.FanoutResult second = ClaimRemovalFanout.apply(
                claimUuid, chunks, treasury, settlements, governors, occupations, revolts, null);

        assertTrue(first.treasuryLedgerRemoved());
        assertTrue(first.settlementSnapshotRemoved());
        assertFalse(second.treasuryLedgerRemoved(), "second pass observes nothing to remove");
        assertFalse(second.settlementSnapshotRemoved());
        assertFalse(second.governorSnapshotRemoved());
        assertTrue(second.removedOccupationIds().isEmpty());
        assertTrue(second.removedRevoltIds().isEmpty());
    }

    @Test
    void fanoutNoOpWhenClaimUuidIsNull() {
        ClaimRemovalFanout.FanoutResult result = ClaimRemovalFanout.apply(
                null, List.of(new ChunkPos(0, 0)), new BannerModTreasuryManager(),
                new SettlementManager(), new BannerModGovernorManager(),
                new OccupationRuntime(), new RevoltRuntime(), null);
        assertFalse(result.treasuryLedgerRemoved());
        assertFalse(result.settlementSnapshotRemoved());
        assertFalse(result.governorSnapshotRemoved());
        assertEquals(0, result.workersDetached());
        assertTrue(result.removedOccupationIds().isEmpty());
        assertTrue(result.removedRevoltIds().isEmpty());
    }
}
