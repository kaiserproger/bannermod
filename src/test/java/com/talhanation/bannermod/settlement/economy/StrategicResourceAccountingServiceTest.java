package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StrategicResourceAccountingServiceTest {

    @Test
    void debitsCoinsThroughTreasuryLedger() {
        UUID claimUuid = UUID.randomUUID();
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        treasury.depositTaxes(claimUuid, new ChunkPos(2, 3), "blueguild", 15, 10L);

        StrategicResourceAccountingService.TransactionResult result = StrategicResourceAccountingService.debit(
                treasury,
                new StrategicResourceAccountingManager(),
                claimUuid,
                StrategicResourceBucket.COINS,
                9,
                20L
        );

        assertEquals(StrategicResourceAccountingService.Status.SUCCESS, result.status());
        assertEquals(StrategicResourceBucket.COINS, result.bucket());
        assertEquals(9, result.appliedAmount());
        assertEquals(0, result.missingAmount());
        assertEquals(6, result.balanceAfter());
        assertEquals("treasury", result.source());
        assertEquals(6, treasury.getLedger(claimUuid).treasuryBalance());
    }

    @Test
    void debitsNonCoinResourceFromSettlementAccounting() {
        UUID claimUuid = UUID.randomUUID();
        StrategicResourceAccountingManager accounting = new StrategicResourceAccountingManager();
        accounting.credit(claimUuid, StrategicResourceBucket.WOOD, 12, 10L);

        StrategicResourceAccountingService.TransactionResult result = StrategicResourceAccountingService.debit(
                new BannerModTreasuryManager(),
                accounting,
                claimUuid,
                StrategicResourceBucket.WOOD,
                5,
                20L
        );

        assertEquals(StrategicResourceAccountingService.Status.SUCCESS, result.status());
        assertEquals(StrategicResourceBucket.WOOD, result.bucket());
        assertEquals(5, result.appliedAmount());
        assertEquals(7, result.balanceAfter());
        assertEquals(7, accounting.getAccount(claimUuid).wood());
    }

    @Test
    void failedCoinDebitLeavesTreasuryUnchangedAndReportsMissingAmount() {
        UUID claimUuid = UUID.randomUUID();
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        treasury.depositTaxes(claimUuid, new ChunkPos(2, 3), "blueguild", 4, 10L);
        BannerModTreasuryLedgerSnapshot before = treasury.getLedger(claimUuid);

        StrategicResourceAccountingService.TransactionResult result = StrategicResourceAccountingService.debit(
                treasury,
                new StrategicResourceAccountingManager(),
                claimUuid,
                StrategicResourceBucket.COINS,
                9,
                20L
        );

        assertEquals(StrategicResourceAccountingService.Status.SHORTAGE, result.status());
        assertEquals(StrategicResourceBucket.COINS, result.bucket());
        assertEquals(5, result.missingAmount());
        assertEquals(0, result.appliedAmount());
        assertEquals(4, result.balanceAfter());
        assertEquals(before, treasury.getLedger(claimUuid));
    }

    @Test
    void failedNonCoinDebitLeavesAccountingUnchangedAndReportsMissingAmount() {
        UUID claimUuid = UUID.randomUUID();
        StrategicResourceAccountingManager accounting = new StrategicResourceAccountingManager();
        accounting.credit(claimUuid, StrategicResourceBucket.IRON, 3, 10L);
        StrategicResourceAccountSnapshot before = accounting.getAccount(claimUuid);

        StrategicResourceAccountingService.TransactionResult result = StrategicResourceAccountingService.debit(
                new BannerModTreasuryManager(),
                accounting,
                claimUuid,
                StrategicResourceBucket.IRON,
                8,
                20L
        );

        assertEquals(StrategicResourceAccountingService.Status.SHORTAGE, result.status());
        assertEquals(StrategicResourceBucket.IRON, result.bucket());
        assertEquals(5, result.missingAmount());
        assertEquals(0, result.appliedAmount());
        assertEquals(3, result.balanceAfter());
        assertEquals(before, accounting.getAccount(claimUuid));
    }

    @Test
    void missingNonCoinAccountReportsSourceUnavailableWithoutCreatingBalance() {
        UUID claimUuid = UUID.randomUUID();
        StrategicResourceAccountingManager accounting = new StrategicResourceAccountingManager();

        StrategicResourceAccountingService.TransactionResult result = StrategicResourceAccountingService.debit(
                new BannerModTreasuryManager(),
                accounting,
                claimUuid,
                StrategicResourceBucket.STONE,
                2,
                20L
        );

        assertEquals(StrategicResourceAccountingService.Status.SOURCE_UNAVAILABLE, result.status());
        assertEquals(StrategicResourceBucket.STONE, result.bucket());
        assertEquals(2, result.missingAmount());
        assertEquals("settlement_accounting_unavailable", result.reason());
        assertNull(accounting.getAccount(claimUuid));
    }

    @Test
    void creditsCoinsThroughTreasuryIdentityFromSettlementSnapshot() {
        UUID claimUuid = UUID.randomUUID();
        BannerModTreasuryManager treasury = new BannerModTreasuryManager();
        SettlementManager settlements = new SettlementManager();
        settlements.putSnapshot(SettlementSnapshot.create(claimUuid, new ChunkPos(4, 5), "blueguild"));

        StrategicResourceAccountingService.TransactionResult result = StrategicResourceAccountingService.credit(
                treasury,
                settlements,
                new StrategicResourceAccountingManager(),
                claimUuid,
                StrategicResourceBucket.COINS,
                11,
                30L
        );

        assertEquals(StrategicResourceAccountingService.Status.SUCCESS, result.status());
        assertEquals(11, result.balanceAfter());
        assertNotNull(treasury.getLedger(claimUuid));
        assertEquals(new ChunkPos(4, 5), treasury.getLedger(claimUuid).anchorChunk());
    }

    @Test
    void nonCoinAccountingPersistsBalancesAndReceiptFields() {
        UUID claimUuid = UUID.randomUUID();
        StrategicResourceAccountingManager accounting = new StrategicResourceAccountingManager();
        accounting.credit(claimUuid, StrategicResourceBucket.FOOD, 10, 30L);
        accounting.debit(claimUuid, StrategicResourceBucket.FOOD, 4, 40L);

        StrategicResourceAccountingManager restored = StrategicResourceAccountingManager.load(accounting.save(new CompoundTag(), null), null);
        StrategicResourceAccountSnapshot restoredAccount = restored.getAccount(claimUuid);

        assertNotNull(restoredAccount);
        assertEquals(6, restoredAccount.food());
        assertEquals("food", restoredAccount.lastBucketId());
        assertEquals(10, restoredAccount.lastCreditAmount());
        assertEquals(30L, restoredAccount.lastCreditTick());
        assertEquals(4, restoredAccount.lastDebitAmount());
        assertEquals(40L, restoredAccount.lastDebitTick());
    }
}
