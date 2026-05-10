package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.settlement.SettlementManager;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

public final class StrategicResourceAccountingService {
    private StrategicResourceAccountingService() {
    }

    public static TransactionResult credit(ServerLevel level,
                                           UUID claimUuid,
                                           StrategicResourceBucket bucket,
                                           int amount,
                                           long tick) {
        return credit(
                BannerModTreasuryManager.get(level),
                SettlementManager.get(level),
                StrategicResourceAccountingManager.get(level),
                claimUuid,
                bucket,
                amount,
                tick
        );
    }

    public static TransactionResult debit(ServerLevel level,
                                          UUID claimUuid,
                                          StrategicResourceBucket bucket,
                                          int amount,
                                          long tick) {
        return debit(
                BannerModTreasuryManager.get(level),
                StrategicResourceAccountingManager.get(level),
                claimUuid,
                bucket,
                amount,
                tick
        );
    }

    static TransactionResult credit(BannerModTreasuryManager treasuryManager,
                                    SettlementManager settlementManager,
                                    StrategicResourceAccountingManager accountingManager,
                                    UUID claimUuid,
                                    StrategicResourceBucket bucket,
                                    int amount,
                                    long tick) {
        int normalizedAmount = Math.max(0, amount);
        if (claimUuid == null || bucket == null) {
            return TransactionResult.sourceUnavailable(bucket, normalizedAmount, "invalid_request");
        }
        if (bucket.treasuryBacked()) {
            BannerModTreasuryLedgerSnapshot ledger = treasuryManager.getLedger(claimUuid);
            SettlementSnapshot snapshot = settlementManager.getSnapshot(claimUuid);
            ChunkPos anchorChunk = ledger != null ? ledger.anchorChunk() : snapshot != null ? snapshot.anchorChunk() : null;
            if (anchorChunk == null) {
                return TransactionResult.sourceUnavailable(bucket, normalizedAmount, "treasury_identity_unavailable");
            }
            String settlementFactionId = ledger != null ? ledger.settlementFactionId() : snapshot.settlementFactionId();
            BannerModTreasuryLedgerSnapshot updated = treasuryManager.depositTaxes(claimUuid, anchorChunk, settlementFactionId, normalizedAmount, tick);
            return TransactionResult.success(bucket, normalizedAmount, normalizedAmount, updated.treasuryBalance(), "treasury");
        }

        StrategicResourceAccountSnapshot updated = accountingManager.credit(claimUuid, bucket, normalizedAmount, tick);
        return TransactionResult.success(bucket, normalizedAmount, normalizedAmount, updated.balance(bucket), "settlement_accounting");
    }

    static TransactionResult debit(BannerModTreasuryManager treasuryManager,
                                   StrategicResourceAccountingManager accountingManager,
                                   UUID claimUuid,
                                   StrategicResourceBucket bucket,
                                   int amount,
                                   long tick) {
        int normalizedAmount = Math.max(0, amount);
        if (claimUuid == null || bucket == null) {
            return TransactionResult.sourceUnavailable(bucket, normalizedAmount, "invalid_request");
        }
        if (bucket.treasuryBacked()) {
            BannerModTreasuryLedgerSnapshot ledger = treasuryManager.getLedger(claimUuid);
            if (ledger == null) {
                return TransactionResult.sourceUnavailable(bucket, normalizedAmount, "treasury_unavailable");
            }
            int balance = ledger.treasuryBalance();
            if (balance < normalizedAmount) {
                return TransactionResult.shortage(bucket, normalizedAmount, normalizedAmount - balance, balance, "treasury");
            }
            BannerModTreasuryLedgerSnapshot updated = ledger.withArmyUpkeepDebit(normalizedAmount, tick);
            treasuryManager.putLedger(updated);
            return TransactionResult.success(bucket, normalizedAmount, normalizedAmount, updated.treasuryBalance(), "treasury");
        }

        StrategicResourceAccountSnapshot account = accountingManager.getAccount(claimUuid);
        if (account == null) {
            return TransactionResult.sourceUnavailable(bucket, normalizedAmount, "settlement_accounting_unavailable");
        }
        int balance = account.balance(bucket);
        if (balance < normalizedAmount) {
            return TransactionResult.shortage(bucket, normalizedAmount, normalizedAmount - balance, balance, "settlement_accounting");
        }
        StrategicResourceAccountSnapshot updated = accountingManager.debit(claimUuid, bucket, normalizedAmount, tick);
        return TransactionResult.success(bucket, normalizedAmount, normalizedAmount, updated.balance(bucket), "settlement_accounting");
    }

    public enum Status {
        SUCCESS,
        SHORTAGE,
        SOURCE_UNAVAILABLE
    }

    public record TransactionResult(
            Status status,
            StrategicResourceBucket bucket,
            int requestedAmount,
            int appliedAmount,
            int missingAmount,
            int balanceAfter,
            String source,
            String reason
    ) {
        private static TransactionResult success(StrategicResourceBucket bucket,
                                                 int requestedAmount,
                                                 int appliedAmount,
                                                 int balanceAfter,
                                                 String source) {
            return new TransactionResult(Status.SUCCESS, bucket, requestedAmount, appliedAmount, 0, balanceAfter, source, "");
        }

        private static TransactionResult shortage(StrategicResourceBucket bucket,
                                                  int requestedAmount,
                                                  int missingAmount,
                                                  int balanceAfter,
                                                  String source) {
            return new TransactionResult(Status.SHORTAGE, bucket, requestedAmount, 0, missingAmount, balanceAfter, source, "shortage");
        }

        private static TransactionResult sourceUnavailable(StrategicResourceBucket bucket, int requestedAmount, String reason) {
            return new TransactionResult(Status.SOURCE_UNAVAILABLE, bucket, requestedAmount, 0, requestedAmount, 0, "", reason);
        }
    }
}
