package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.settlement.economy.StrategicResourceAccountingManager;
import com.talhanation.bannermod.settlement.economy.StrategicResourceBucket;
import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatyRuntimeTest {
    private static RecruitsClaim claim(String name, UUID owner, ChunkPos pos) {
        RecruitsClaim claim = new RecruitsClaim(name, owner);
        claim.setCenter(pos);
        claim.addChunk(pos);
        return claim;
    }

    @Test
    void dueTributeTransfersTreasuryFunds() {
        UUID payer = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        TreatyRuntime treaties = new TreatyRuntime();
        TributeTreatyRecord treaty = treaties.addTribute(payer, receiver, StrategicResourceBucket.COINS,
                10, 100L, UUID.randomUUID(), null, 0L);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        RecruitsClaimManager claims = new RecruitsClaimManager();
        RecruitsClaim payerClaim = claim("payer", payer, new ChunkPos(0, 0));
        RecruitsClaim receiverClaim = claim("receiver", receiver, new ChunkPos(10, 10));
        claims.testInsertClaim(payerClaim);
        claims.testInsertClaim(receiverClaim);
        treasury.depositTaxes(payerClaim.getUUID(), payerClaim.getCenter(), null, 25, 0L);

        TreatyPaymentRuntime payments = new TreatyPaymentRuntime(treaties, treasury,
                new StrategicResourceAccountingManager(), claims, new WarAuditLogSavedData());
        payments.processDue(100L);

        assertEquals(15, treasury.getLedger(payerClaim.getUUID()).treasuryBalance());
        assertEquals(10, treasury.getLedger(receiverClaim.getUUID()).treasuryBalance());
        assertEquals(100L, treaties.tributeTreaties().iterator().next().lastPaidAtGameTime());
        assertEquals(treaty.id(), treaties.tributeTreaties().iterator().next().id());
    }

    @Test
    void missingTributeRecordsDefaultFactAndMissedPayment() {
        UUID payer = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        TreatyRuntime treaties = new TreatyRuntime();
        treaties.addTribute(payer, receiver, StrategicResourceBucket.COINS,
                20, 100L, UUID.randomUUID(), null, 0L);
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        RecruitsClaimManager claims = new RecruitsClaimManager();
        RecruitsClaim payerClaim = claim("payer", payer, new ChunkPos(0, 0));
        RecruitsClaim receiverClaim = claim("receiver", receiver, new ChunkPos(10, 10));
        claims.testInsertClaim(payerClaim);
        claims.testInsertClaim(receiverClaim);
        treasury.depositTaxes(payerClaim.getUUID(), payerClaim.getCenter(), null, 5, 0L);

        TreatyPaymentRuntime payments = new TreatyPaymentRuntime(treaties, treasury,
                new StrategicResourceAccountingManager(), claims, new WarAuditLogSavedData());
        payments.processDue(100L);

        TributeTreatyRecord updated = treaties.tributeTreaties().iterator().next();
        assertEquals(1, updated.missedPayments());
        assertEquals(15, updated.defaultedAmount());
        assertEquals(1, treaties.defaultFacts().size());
        TreatyDefaultFact fact = treaties.defaultFacts().iterator().next();
        assertEquals(20, fact.requestedAmount());
        assertEquals(5, fact.paidAmount());
        assertEquals(15, fact.defaultedAmount());
        assertEquals(100L, updated.lastPaidAtGameTime());
    }

    @Test
    void dueTributeTransfersStrategicResources() {
        UUID payer = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        TreatyRuntime treaties = new TreatyRuntime();
        treaties.addTribute(payer, receiver, StrategicResourceBucket.IRON,
                8, 100L, UUID.randomUUID(), null, 0L);
        RecruitsClaimManager claims = new RecruitsClaimManager();
        RecruitsClaim payerClaim = claim("payer", payer, new ChunkPos(0, 0));
        RecruitsClaim receiverClaim = claim("receiver", receiver, new ChunkPos(10, 10));
        claims.testInsertClaim(payerClaim);
        claims.testInsertClaim(receiverClaim);
        StrategicResourceAccountingManager resources = new StrategicResourceAccountingManager();
        resources.credit(payerClaim.getUUID(), StrategicResourceBucket.IRON, 12, 0L);

        TreatyPaymentRuntime payments = new TreatyPaymentRuntime(treaties, new BannerModTreasuryManager(),
                resources, claims, new WarAuditLogSavedData());
        payments.processDue(100L);

        assertEquals(4, resources.getAccount(payerClaim.getUUID()).balance(StrategicResourceBucket.IRON));
        assertEquals(8, resources.getAccount(receiverClaim.getUUID()).balance(StrategicResourceBucket.IRON));
        assertEquals(0, treaties.defaultFacts().size());
    }

    @Test
    void vassalRecordIdentifiesPartiesAndObligations() {
        UUID overlord = UUID.randomUUID();
        UUID vassal = UUID.randomUUID();
        TreatyRuntime treaties = new TreatyRuntime();
        VassalRelationshipRecord relationship = treaties.addVassalRelationship(overlord, vassal,
                "military_support,overlord_diplomacy", 3, 100L, UUID.randomUUID(), 0L);

        assertEquals(overlord, relationship.overlordEntityId());
        assertEquals(vassal, relationship.vassalEntityId());
        assertTrue(relationship.obligations().contains("military_support"));
        assertEquals(1, treaties.vassalRelationships().size());
    }
}
