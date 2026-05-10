package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.governance.BannerModGovernorHeartbeat;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.shared.logistics.BannerModSupplyStatus;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SettlementTreasuryDerivationServiceTest {
    @Test
    void collectedTaxesDeriveClaimLedgerAndFiscalRollup() {
        UUID claimUuid = UUID.randomUUID();
        BannerModGovernorSnapshot snapshot = BannerModGovernorSnapshot.create(claimUuid, new ChunkPos(4, 7), "blueguild");
        BannerModTreasuryManager treasuryManager = new BannerModTreasuryManager();
        BannerModSettlementBinding.Binding binding = new BannerModSettlementBinding.Binding(BannerModSettlementBinding.Status.FRIENDLY_CLAIM, "blueguild", "blueguild");
        BannerModGovernorHeartbeat.HeartbeatReport report = new BannerModGovernorHeartbeat.HeartbeatReport(6, 12, 12, List.of(), List.of(), 100L, 100L);

        BannerModTreasuryLedgerSnapshot.FiscalRollup rollup = SettlementTreasuryDerivationService.deriveHeartbeatAccounting(
                treasuryManager,
                snapshot,
                binding,
                report,
                stableRecruitSupply()
        );

        BannerModTreasuryLedgerSnapshot ledger = treasuryManager.getLedger(claimUuid);
        assertNotNull(ledger);
        assertNotNull(rollup);
        assertEquals("blueguild", ledger.settlementFactionId());
        assertEquals(12, ledger.accruedTaxes());
        assertEquals(0, ledger.spentArmyUpkeep());
        assertEquals(12, ledger.lastDepositAmount());
        assertEquals(0, ledger.lastArmyUpkeepDebitAmount());
        assertEquals(100L, ledger.lastDepositTick());
        assertEquals(12, rollup.treasuryBalance());
        assertEquals(12, rollup.lastNetChange());
        assertEquals(24, rollup.projectedNextBalance());
    }

    @Test
    void emptyHeartbeatDoesNotCreateTreasuryLedger() {
        UUID claimUuid = UUID.randomUUID();
        BannerModGovernorSnapshot snapshot = BannerModGovernorSnapshot.create(claimUuid, new ChunkPos(2, 9), "blueguild");
        BannerModTreasuryManager treasuryManager = new BannerModTreasuryManager();

        BannerModTreasuryLedgerSnapshot.FiscalRollup rollup = SettlementTreasuryDerivationService.deriveHeartbeatAccounting(
                treasuryManager,
                snapshot,
                new BannerModSettlementBinding.Binding(BannerModSettlementBinding.Status.HOSTILE_CLAIM, "blueguild", "redguild"),
                new BannerModGovernorHeartbeat.HeartbeatReport(4, 8, 0, List.of(), List.of(), 120L, 90L),
                stableRecruitSupply()
        );

        assertNull(treasuryManager.getLedger(claimUuid));
        assertNotNull(rollup);
        assertEquals(0, rollup.treasuryBalance());
        assertEquals(0, rollup.lastNetChange());
    }

    @Test
    void unpaidRecruitUpkeepDerivesTreasuryDebitInSameAccountingPass() {
        UUID claimUuid = UUID.randomUUID();
        BannerModGovernorSnapshot snapshot = BannerModGovernorSnapshot.create(claimUuid, new ChunkPos(4, 7), "blueguild");
        BannerModTreasuryManager treasuryManager = new BannerModTreasuryManager();
        BannerModSettlementBinding.Binding binding = new BannerModSettlementBinding.Binding(BannerModSettlementBinding.Status.FRIENDLY_CLAIM, "blueguild", "blueguild");
        BannerModGovernorHeartbeat.HeartbeatReport report = new BannerModGovernorHeartbeat.HeartbeatReport(6, 12, 12, List.of(), List.of(), 100L, 100L);

        BannerModTreasuryLedgerSnapshot.FiscalRollup rollup = SettlementTreasuryDerivationService.deriveHeartbeatAccounting(
                treasuryManager,
                snapshot,
                binding,
                report,
                unpaidRecruitSupply()
        );

        BannerModTreasuryLedgerSnapshot ledger = treasuryManager.getLedger(claimUuid);
        assertNotNull(ledger);
        assertNotNull(rollup);
        assertEquals(12, ledger.accruedTaxes());
        assertEquals(1, ledger.spentArmyUpkeep());
        assertEquals(11, ledger.treasuryBalance());
        assertEquals(1, ledger.lastArmyUpkeepDebitAmount());
        assertEquals(100L, ledger.lastArmyUpkeepDebitTick());
        assertEquals(11, rollup.treasuryBalance());
        assertEquals(11, rollup.lastNetChange());
        assertEquals(22, rollup.projectedNextBalance());
        assertEquals(100L, rollup.accountingTick());
    }

    @Test
    void coarseArmyUpkeepRequestDebitsTreasuryEvenWhenIndividualPaymentIsCurrent() {
        UUID claimUuid = UUID.randomUUID();
        BannerModGovernorSnapshot snapshot = BannerModGovernorSnapshot.create(claimUuid, new ChunkPos(4, 7), "blueguild");
        BannerModTreasuryManager treasuryManager = new BannerModTreasuryManager();
        BannerModSettlementBinding.Binding binding = new BannerModSettlementBinding.Binding(BannerModSettlementBinding.Status.FRIENDLY_CLAIM, "blueguild", "blueguild");
        BannerModGovernorHeartbeat.HeartbeatReport report = new BannerModGovernorHeartbeat.HeartbeatReport(6, 12, 12, List.of(), List.of(), 100L, 100L);

        BannerModTreasuryLedgerSnapshot.FiscalRollup rollup = SettlementTreasuryDerivationService.deriveHeartbeatAccounting(
                treasuryManager,
                snapshot,
                binding,
                report,
                stableRecruitSupply(),
                5
        );

        BannerModTreasuryLedgerSnapshot ledger = treasuryManager.getLedger(claimUuid);
        assertNotNull(ledger);
        assertNotNull(rollup);
        assertEquals(12, ledger.accruedTaxes());
        assertEquals(5, ledger.spentArmyUpkeep());
        assertEquals(7, ledger.treasuryBalance());
        assertEquals(5, ledger.lastArmyUpkeepDebitAmount());
        assertEquals(7, rollup.treasuryBalance());
    }

    @Test
    void fiscalRollupCanBeAppliedToGovernorSnapshot() {
        UUID claimUuid = UUID.randomUUID();
        BannerModGovernorSnapshot snapshot = BannerModGovernorSnapshot.create(claimUuid, new ChunkPos(4, 7), "blueguild");
        BannerModGovernorHeartbeat.HeartbeatReport report = new BannerModGovernorHeartbeat.HeartbeatReport(6, 12, 12, List.of(), List.of(), 100L, 100L);

        BannerModTreasuryLedgerSnapshot.FiscalRollup rollup = SettlementTreasuryDerivationService.deriveHeartbeatAccounting(
                new BannerModTreasuryManager(),
                snapshot,
                new BannerModSettlementBinding.Binding(BannerModSettlementBinding.Status.FRIENDLY_CLAIM, "blueguild", "blueguild"),
                report,
                unpaidRecruitSupply()
        );

        BannerModGovernorSnapshot updatedSnapshot = snapshot.withHeartbeatReport(
                report.heartbeatTick(),
                report.collectionTick(),
                report.citizenCount(),
                report.taxesDue(),
                report.taxesCollected(),
                List.of(),
                List.of()
        ).withFiscalRollup(rollup);

        assertEquals(11, updatedSnapshot.treasuryBalance());
        assertEquals(11, updatedSnapshot.lastTreasuryNet());
        assertEquals(22, updatedSnapshot.projectedTreasuryBalance());
    }

    private static BannerModSupplyStatus.RecruitSupplyStatus stableRecruitSupply() {
        return new BannerModSupplyStatus.RecruitSupplyStatus(
                BannerModSupplyStatus.RecruitSupplyState.READY,
                false,
                false,
                false,
                null,
                BannerModSupplyStatus.armyUpkeepStatus(false, false, 100.0F)
        );
    }

    private static BannerModSupplyStatus.RecruitSupplyStatus unpaidRecruitSupply() {
        return new BannerModSupplyStatus.RecruitSupplyStatus(
                BannerModSupplyStatus.RecruitSupplyState.NEEDS_PAYMENT,
                true,
                false,
                true,
                "recruit_upkeep_missing_payment",
                BannerModSupplyStatus.armyUpkeepStatus(true, false, 100.0F)
        );
    }
}
