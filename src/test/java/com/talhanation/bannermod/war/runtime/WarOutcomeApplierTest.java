package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.war.audit.WarAuditEntry;
import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;
import com.talhanation.bannermod.war.registry.PoliticalRegistryRuntime;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarOutcomeApplierTest {

    private static UUID seed(WarDeclarationRuntime runtime, UUID attacker, UUID defender, WarState state) {
        WarDeclarationRecord record = runtime.declareWar(
                attacker, defender, WarGoalType.WHITE_PEACE, "",
                List.of(), List.of(), List.of(), 0L, 0L
        ).orElseThrow();
        if (state != WarState.DECLARED) {
            runtime.updateState(record.id(), state);
        }
        return record.id();
    }

    private static WarOutcomeApplier applier(WarDeclarationRuntime declarations,
                                             WarAuditLogSavedData audit) {
        return new WarOutcomeApplier(
                declarations,
                new SiegeStandardRuntime(),
                audit,
                new OccupationRuntime(),
                new DemilitarizationRuntime(),
                new PoliticalRegistryRuntime()
        );
    }

    private static WarOutcomeApplier applierWith(WarDeclarationRuntime declarations,
                                                 WarAuditLogSavedData audit,
                                                 OccupationRuntime occupations,
                                                 BannerModTreasuryManager treasury,
                                                 RecruitsClaimManager claimManager) {
        return new WarOutcomeApplier(
                declarations,
                new SiegeStandardRuntime(),
                audit,
                occupations,
                new DemilitarizationRuntime(),
                new PoliticalRegistryRuntime(),
                null, 0L,
                treasury, claimManager
        );
    }

    private static RecruitsClaim singleChunkClaim(String name, UUID owner, ChunkPos pos) {
        RecruitsClaim claim = new RecruitsClaim(name, owner);
        claim.setCenter(pos);
        claim.addChunk(pos);
        return claim;
    }

    private static RecruitsClaim multiChunkClaim(String name, UUID owner, ChunkPos center, ChunkPos... extras) {
        RecruitsClaim claim = new RecruitsClaim(name, owner);
        claim.setCenter(center);
        claim.addChunk(center);
        for (ChunkPos extra : extras) claim.addChunk(extra);
        return claim;
    }

    @Test
    void whitePeaceResolvesAndAudits() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID warId = seed(runtime, UUID.randomUUID(), UUID.randomUUID(), WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        WarOutcomeApplier applier = applier(runtime, audit);

        WarOutcomeApplier.Result result = applier.applyWhitePeace(warId, 100L);
        assertTrue(result.valid());
        assertEquals(WarOutcomeType.WHITE_PEACE, result.outcome());
        assertEquals(WarState.RESOLVED, runtime.byId(warId).orElseThrow().state());
        assertEquals(1, audit.all().size());
        WarAuditEntry entry = audit.all().get(0);
        assertEquals(warId, entry.warId());
        assertEquals("OUTCOME_APPLIED", entry.type());
    }

    @Test
    void tributeRejectsNegativeAmount() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID warId = seed(runtime, UUID.randomUUID(), UUID.randomUUID(), WarState.DECLARED);
        WarOutcomeApplier applier = applier(runtime, new WarAuditLogSavedData());

        WarOutcomeApplier.Result result = applier.applyTribute(warId, -5L, 0L);
        assertFalse(result.valid());
        assertEquals("negative_tribute", result.reason());
    }

    @Test
    void cancelOnlyAllowedFromDeclared() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID warId = seed(runtime, UUID.randomUUID(), UUID.randomUUID(), WarState.ACTIVE);
        WarOutcomeApplier applier = applier(runtime, new WarAuditLogSavedData());

        WarOutcomeApplier.Result result = applier.cancel(warId, 0L, "test");
        assertFalse(result.valid());
        assertEquals("not_cancellable", result.reason());
    }

    @Test
    void unknownWarReturnsInvalid() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        WarOutcomeApplier applier = applier(runtime, new WarAuditLogSavedData());
        WarOutcomeApplier.Result result = applier.applyWhitePeace(UUID.randomUUID(), 0L);
        assertFalse(result.valid());
        assertEquals("unknown_war", result.reason());
        assertNotNull(result);
    }

    // ------------------------------------------------------------------------
    // applyOccupy
    // ------------------------------------------------------------------------

    @Test
    void applyOccupy_placesOccupationAndResolvesWar() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        OccupationRuntime occupations = new OccupationRuntime();
        WarOutcomeApplier applier = applierWith(runtime, audit, occupations, null, null);

        WarOutcomeApplier.Result result = applier.applyOccupy(warId, List.of(new ChunkPos(2, 3)), 50L);

        assertTrue(result.valid());
        assertEquals(WarOutcomeType.OCCUPATION, result.outcome());
        assertEquals(WarState.RESOLVED, runtime.byId(warId).orElseThrow().state());
        assertEquals(1, occupations.all().size());
        WarAuditEntry entry = audit.all().get(0);
        assertTrue(entry.detail().contains("type=OCCUPATION"));
        assertTrue(entry.detail().contains("chunks=1"));
    }

    @Test
    void applyOccupy_returnsInvalidWhenChunksEmpty() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID warId = seed(runtime, UUID.randomUUID(), UUID.randomUUID(), WarState.DECLARED);
        WarOutcomeApplier applier = applierWith(runtime, new WarAuditLogSavedData(),
                new OccupationRuntime(), null, null);

        WarOutcomeApplier.Result result = applier.applyOccupy(warId, List.of(), 0L);
        assertFalse(result.valid());
        assertEquals("no_chunks", result.reason());
        assertEquals(WarState.DECLARED, runtime.byId(warId).orElseThrow().state());
    }

    @Test
    void applyOccupy_returnsInvalidWhenWarAlreadyResolved() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID warId = seed(runtime, UUID.randomUUID(), UUID.randomUUID(), WarState.RESOLVED);
        WarOutcomeApplier applier = applierWith(runtime, new WarAuditLogSavedData(),
                new OccupationRuntime(), null, null);

        WarOutcomeApplier.Result result = applier.applyOccupy(warId, List.of(new ChunkPos(0, 0)), 0L);
        assertFalse(result.valid());
        assertEquals("already_closed", result.reason());
    }

    @Test
    void applyOccupy_acceptsMultipleChunks() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID warId = seed(runtime, UUID.randomUUID(), UUID.randomUUID(), WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        OccupationRuntime occupations = new OccupationRuntime();
        WarOutcomeApplier applier = applierWith(runtime, audit, occupations, null, null);

        List<ChunkPos> chunks = List.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(0, 1));
        WarOutcomeApplier.Result result = applier.applyOccupy(warId, chunks, 0L);

        assertTrue(result.valid());
        assertEquals(1, occupations.all().size());
        assertEquals(3, occupations.all().iterator().next().chunks().size());
        assertTrue(audit.all().get(0).detail().contains("chunks=3"));
    }

    // ------------------------------------------------------------------------
    // applyTribute treasury transfer
    // ------------------------------------------------------------------------

    @Test
    void applyTribute_transfersUpToAvailableBalance() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        RecruitsClaimManager claims = new RecruitsClaimManager();

        RecruitsClaim defenderClaim = singleChunkClaim("D", defender, new ChunkPos(0, 0));
        RecruitsClaim attackerClaim = singleChunkClaim("A", attacker, new ChunkPos(10, 10));
        claims.testInsertClaim(defenderClaim);
        claims.testInsertClaim(attackerClaim);
        treasury.depositTaxes(defenderClaim.getUUID(), defenderClaim.getCenter(), null, 30, 0L);

        WarOutcomeApplier applier = applierWith(runtime, audit, new OccupationRuntime(), treasury, claims);
        WarOutcomeApplier.Result result = applier.applyTribute(warId, 50L, 100L);

        assertTrue(result.valid());
        assertTrue(audit.all().get(0).detail().contains("amount=50"));
        assertTrue(audit.all().get(0).detail().contains("transferred=30"));
        assertEquals(0, treasury.getLedger(defenderClaim.getUUID()).treasuryBalance());
        assertEquals(30, treasury.getLedger(attackerClaim.getUUID()).treasuryBalance());
    }

    @Test
    void applyTribute_transfersZeroWhenLoserHasNoLedger() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        RecruitsClaimManager claims = new RecruitsClaimManager();
        claims.testInsertClaim(singleChunkClaim("A", attacker, new ChunkPos(0, 0)));

        WarOutcomeApplier applier = applierWith(runtime, audit, new OccupationRuntime(), treasury, claims);
        WarOutcomeApplier.Result result = applier.applyTribute(warId, 10L, 0L);

        assertTrue(result.valid());
        assertTrue(audit.all().get(0).detail().contains("transferred=0"));
        assertEquals(WarState.RESOLVED, runtime.byId(warId).orElseThrow().state());
    }

    @Test
    void applyTribute_iteratesMultipleDefenderClaims() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        RecruitsClaimManager claims = new RecruitsClaimManager();

        RecruitsClaim d1 = singleChunkClaim("D1", defender, new ChunkPos(0, 0));
        RecruitsClaim d2 = singleChunkClaim("D2", defender, new ChunkPos(1, 0));
        RecruitsClaim a = singleChunkClaim("A", attacker, new ChunkPos(10, 10));
        claims.testInsertClaim(d1);
        claims.testInsertClaim(d2);
        claims.testInsertClaim(a);
        treasury.depositTaxes(d1.getUUID(), d1.getCenter(), null, 20, 0L);
        treasury.depositTaxes(d2.getUUID(), d2.getCenter(), null, 25, 0L);

        WarOutcomeApplier applier = applierWith(runtime, audit, new OccupationRuntime(), treasury, claims);
        WarOutcomeApplier.Result result = applier.applyTribute(warId, 30L, 100L);

        assertTrue(result.valid());
        assertTrue(audit.all().get(0).detail().contains("transferred=30"));
        int aBalance = treasury.getLedger(a.getUUID()).treasuryBalance();
        int defenderTotal = treasury.getLedger(d1.getUUID()).treasuryBalance()
                + treasury.getLedger(d2.getUUID()).treasuryBalance();
        assertEquals(30, aBalance);
        assertEquals(15, defenderTotal); // (20+25) - 30
    }

    @Test
    void applyTribute_transfersZeroWhenWinnerHasNoClaim() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        RecruitsClaimManager claims = new RecruitsClaimManager();

        RecruitsClaim defenderClaim = singleChunkClaim("D", defender, new ChunkPos(0, 0));
        claims.testInsertClaim(defenderClaim);
        treasury.depositTaxes(defenderClaim.getUUID(), defenderClaim.getCenter(), null, 30, 0L);

        WarOutcomeApplier applier = applierWith(runtime, audit, new OccupationRuntime(), treasury, claims);
        WarOutcomeApplier.Result result = applier.applyTribute(warId, 50L, 0L);

        assertTrue(result.valid());
        assertTrue(audit.all().get(0).detail().contains("transferred=0"));
        assertEquals(30, treasury.getLedger(defenderClaim.getUUID()).treasuryBalance(),
                "no winner ledger: defender balance must NOT be debited");
    }

    // ------------------------------------------------------------------------
    // applyAnnex (whole-claim transfer via center chunk)
    // ------------------------------------------------------------------------

    @Test
    void applyAnnex_flipsWholeClaimWhenCenterTargeted() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        RecruitsClaimManager claims = new RecruitsClaimManager();
        ChunkPos center = new ChunkPos(5, 5);
        RecruitsClaim claim = multiChunkClaim("D", defender, center,
                new ChunkPos(5, 6), new ChunkPos(6, 5), new ChunkPos(4, 5));
        claims.testInsertClaim(claim);
        List<RecruitsClaim> republished = new ArrayList<>();

        WarOutcomeApplier applier = applierWith(runtime, audit, new OccupationRuntime(), null, claims);
        WarOutcomeApplier.Result result = applier.applyAnnex(warId, center, 100L, republished::add);

        assertTrue(result.valid());
        assertEquals(WarOutcomeType.ANNEX_LIMITED_CHUNKS, result.outcome());
        assertEquals(attacker, claim.getOwnerPoliticalEntityId());
        assertEquals(WarState.RESOLVED, runtime.byId(warId).orElseThrow().state());
        assertEquals(1, republished.size());
        assertTrue(audit.all().get(0).detail().contains("chunks=4"));
        assertTrue(audit.all().get(0).detail().contains("type=ANNEX_LIMITED_CHUNKS"));
    }

    @Test
    void applyAnnex_rejectsNonCenterChunk() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        RecruitsClaimManager claims = new RecruitsClaimManager();
        ChunkPos center = new ChunkPos(0, 0);
        ChunkPos edge = new ChunkPos(1, 0);
        claims.testInsertClaim(multiChunkClaim("D", defender, center, edge));

        WarOutcomeApplier applier = applierWith(runtime, audit, new OccupationRuntime(), null, claims);
        WarOutcomeApplier.Result result = applier.applyAnnex(warId, edge, 0L, c -> { });

        assertFalse(result.valid());
        assertEquals("not_claim_center", result.reason());
        assertEquals(WarState.DECLARED, runtime.byId(warId).orElseThrow().state());
    }

    @Test
    void applyAnnex_rejectsClaimNotOwnedByDefender() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.DECLARED);
        WarAuditLogSavedData audit = new WarAuditLogSavedData();
        RecruitsClaimManager claims = new RecruitsClaimManager();
        ChunkPos center = new ChunkPos(0, 0);
        claims.testInsertClaim(singleChunkClaim("Third", third, center));

        WarOutcomeApplier applier = applierWith(runtime, audit, new OccupationRuntime(), null, claims);
        WarOutcomeApplier.Result result = applier.applyAnnex(warId, center, 0L, c -> { });

        assertFalse(result.valid());
        assertEquals("claim_not_defender_owned", result.reason());
    }

    @Test
    void applyAnnex_rejectsWhenNoClaimAtChunk() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID warId = seed(runtime, UUID.randomUUID(), UUID.randomUUID(), WarState.DECLARED);
        WarOutcomeApplier applier = applierWith(runtime, new WarAuditLogSavedData(),
                new OccupationRuntime(), null, new RecruitsClaimManager());

        WarOutcomeApplier.Result result = applier.applyAnnex(warId, new ChunkPos(0, 0), 0L, c -> { });
        assertFalse(result.valid());
        assertEquals("claim_not_found", result.reason());
    }

    @Test
    void applyAnnex_rejectsWhenClaimManagerNull() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID warId = seed(runtime, UUID.randomUUID(), UUID.randomUUID(), WarState.DECLARED);
        WarOutcomeApplier applier = applierWith(runtime, new WarAuditLogSavedData(),
                new OccupationRuntime(), null, null);

        WarOutcomeApplier.Result result = applier.applyAnnex(warId, new ChunkPos(0, 0), 0L, c -> { });
        assertFalse(result.valid());
        assertEquals("claim_manager_unavailable", result.reason());
    }

    @Test
    void applyAnnex_rejectsWhenWarAlreadyResolved() {
        WarDeclarationRuntime runtime = new WarDeclarationRuntime();
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = seed(runtime, attacker, defender, WarState.RESOLVED);
        RecruitsClaimManager claims = new RecruitsClaimManager();
        ChunkPos center = new ChunkPos(0, 0);
        claims.testInsertClaim(singleChunkClaim("D", defender, center));

        WarOutcomeApplier applier = applierWith(runtime, new WarAuditLogSavedData(),
                new OccupationRuntime(), null, claims);
        WarOutcomeApplier.Result result = applier.applyAnnex(warId, center, 0L, c -> { });
        assertFalse(result.valid());
        assertEquals("already_closed", result.reason());
    }
}
