package com.talhanation.bannermod.settlement.economy;

import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.war.registry.PoliticalEntityAuthority;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FortUpgradeService {
    private FortUpgradeService() {
    }

    public static UpgradeResult planUpgrade(BannerModTreasuryManager treasuryManager,
                                            StrategicResourceAccountingManager accountingManager,
                                            RecruitsClaim claim,
                                            UUID actorUuid,
                                            boolean admin,
                                            @Nullable PoliticalEntityRecord politicalOwner) {
        if (claim == null) {
            return UpgradeResult.denied(DenialReason.INVALID_STATE, "chat.bannermod.fort.upgrade.denied.missing_claim");
        }
        if (!canUpgrade(actorUuid, admin, claim, politicalOwner)) {
            return UpgradeResult.denied(DenialReason.MISSING_AUTHORITY, "chat.bannermod.fort.upgrade.denied.no_authority");
        }

        FortLevelDefinition current = FortLevelDefinition.forLevel(claim.getFortLevel());
        FortLevelDefinition.UpgradeRequirement requirement = current.nextLevelRequirement();
        if (requirement == null || current.level() >= FortLevelDefinition.MAX_LEVEL) {
            return UpgradeResult.denied(DenialReason.INVALID_STATE, "chat.bannermod.fort.upgrade.denied.max_level");
        }

        MissingResource missing = firstMissingResource(treasuryManager, accountingManager, claim.getUUID(), requirement);
        if (missing != null) {
            return UpgradeResult.denied(
                    DenialReason.MISSING_RESOURCES,
                    "chat.bannermod.fort.upgrade.denied.missing_resources",
                    missing.bucket,
                    missing.missingAmount
            );
        }

        int nextLevel = current.level() + 1;
        return UpgradeResult.success(nextLevel, FortLevelDefinition.forLevel(nextLevel));
    }

    public static boolean applyUpgrade(BannerModTreasuryManager treasuryManager,
                                       StrategicResourceAccountingManager accountingManager,
                                       RecruitsClaim claim,
                                       UpgradeResult result,
                                       long tick) {
        if (claim == null || result == null || !result.upgraded()) {
            return false;
        }
        FortLevelDefinition current = FortLevelDefinition.forLevel(claim.getFortLevel());
        FortLevelDefinition.UpgradeRequirement requirement = current.nextLevelRequirement();
        if (requirement == null || result.newLevel() != current.level() + 1) {
            return false;
        }
        if (firstMissingResource(treasuryManager, accountingManager, claim.getUUID(), requirement) != null) {
            return false;
        }
        if (!debitRequirement(treasuryManager, accountingManager, claim.getUUID(), requirement, tick)) {
            return false;
        }
        claim.setFortLevel(result.newLevel());
        return true;
    }

    public static void rollbackUpgrade(BannerModTreasuryManager treasuryManager,
                                       StrategicResourceAccountingManager accountingManager,
                                       RecruitsClaim claim,
                                       int previousLevel,
                                       UpgradeResult result,
                                       long tick) {
        if (claim == null || result == null || !result.upgraded()) {
            return;
        }
        FortLevelDefinition previous = FortLevelDefinition.forLevel(previousLevel);
        if (result.newLevel() != previous.level() + 1 || previous.nextLevelRequirement() == null) {
            return;
        }
        creditRequirement(treasuryManager, accountingManager, claim.getUUID(), previous.nextLevelRequirement(), tick);
        claim.setFortLevel(previous.level());
    }

    private static boolean canUpgrade(UUID actorUuid,
                                      boolean admin,
                                      RecruitsClaim claim,
                                      @Nullable PoliticalEntityRecord politicalOwner) {
        if (actorUuid == null || claim == null) {
            return false;
        }
        if (admin) {
            return true;
        }
        if (claim.getOwnerPoliticalEntityId() != null) {
            return politicalOwner != null && PoliticalEntityAuthority.canAct(actorUuid, false, politicalOwner);
        }
        if (claim.getPlayerInfo() != null && actorUuid.equals(claim.getPlayerInfo().getUUID())) {
            return true;
        }
        return politicalOwner != null && PoliticalEntityAuthority.canAct(actorUuid, false, politicalOwner);
    }

    @Nullable
    private static MissingResource firstMissingResource(BannerModTreasuryManager treasuryManager,
                                                        StrategicResourceAccountingManager accountingManager,
                                                        UUID claimUuid,
                                                        FortLevelDefinition.UpgradeRequirement requirement) {
        MissingResource missing = missingNonCoin(accountingManager, claimUuid, StrategicResourceBucket.FOOD, requirement.food());
        if (missing != null) return missing;
        missing = missingNonCoin(accountingManager, claimUuid, StrategicResourceBucket.IRON, requirement.iron());
        if (missing != null) return missing;
        missing = missingNonCoin(accountingManager, claimUuid, StrategicResourceBucket.WOOD, requirement.wood());
        if (missing != null) return missing;
        missing = missingNonCoin(accountingManager, claimUuid, StrategicResourceBucket.STONE, requirement.stone());
        if (missing != null) return missing;

        BannerModTreasuryLedgerSnapshot ledger = treasuryManager.getLedger(claimUuid);
        int coins = ledger != null ? ledger.treasuryBalance() : 0;
        if (coins < requirement.coins()) {
            return new MissingResource(StrategicResourceBucket.COINS, requirement.coins() - coins);
        }
        return null;
    }

    @Nullable
    private static MissingResource missingNonCoin(StrategicResourceAccountingManager accountingManager,
                                                 UUID claimUuid,
                                                 StrategicResourceBucket bucket,
                                                 int required) {
        StrategicResourceAccountSnapshot account = accountingManager.getAccount(claimUuid);
        int balance = account != null ? account.balance(bucket) : 0;
        if (balance < required) {
            return new MissingResource(bucket, required - balance);
        }
        return null;
    }

    private static boolean debitRequirement(BannerModTreasuryManager treasuryManager,
                                            StrategicResourceAccountingManager accountingManager,
                                            UUID claimUuid,
                                            FortLevelDefinition.UpgradeRequirement requirement,
                                            long tick) {
        List<StrategicResourceAccountingService.TransactionResult> applied = new ArrayList<>();
        if (!debitBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.FOOD, requirement.food(), tick, applied)) return false;
        if (!debitBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.IRON, requirement.iron(), tick, applied)) return false;
        if (!debitBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.WOOD, requirement.wood(), tick, applied)) return false;
        if (!debitBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.STONE, requirement.stone(), tick, applied)) return false;
        return debitBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.COINS, requirement.coins(), tick, applied);
    }

    private static boolean debitBucket(BannerModTreasuryManager treasuryManager,
                                       StrategicResourceAccountingManager accountingManager,
                                       UUID claimUuid,
                                       StrategicResourceBucket bucket,
                                       int amount,
                                       long tick,
                                       List<StrategicResourceAccountingService.TransactionResult> applied) {
        StrategicResourceAccountingService.TransactionResult result = StrategicResourceAccountingService.debit(
                treasuryManager,
                accountingManager,
                claimUuid,
                bucket,
                amount,
                tick
        );
        if (result.status() == StrategicResourceAccountingService.Status.SUCCESS && result.appliedAmount() == amount) {
            applied.add(result);
            return true;
        }
        rollback(treasuryManager, accountingManager, claimUuid, applied, tick);
        return false;
    }

    private static void rollback(BannerModTreasuryManager treasuryManager,
                                 StrategicResourceAccountingManager accountingManager,
                                 UUID claimUuid,
                                 List<StrategicResourceAccountingService.TransactionResult> applied,
                                 long tick) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            StrategicResourceAccountingService.TransactionResult result = applied.get(index);
            creditBucket(treasuryManager, accountingManager, claimUuid, result.bucket(), result.appliedAmount(), tick);
        }
    }

    private static void creditRequirement(BannerModTreasuryManager treasuryManager,
                                          StrategicResourceAccountingManager accountingManager,
                                          UUID claimUuid,
                                          FortLevelDefinition.UpgradeRequirement requirement,
                                          long tick) {
        creditBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.FOOD, requirement.food(), tick);
        creditBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.IRON, requirement.iron(), tick);
        creditBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.WOOD, requirement.wood(), tick);
        creditBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.STONE, requirement.stone(), tick);
        creditBucket(treasuryManager, accountingManager, claimUuid, StrategicResourceBucket.COINS, requirement.coins(), tick);
    }

    private static void creditBucket(BannerModTreasuryManager treasuryManager,
                                     StrategicResourceAccountingManager accountingManager,
                                     UUID claimUuid,
                                     StrategicResourceBucket bucket,
                                     int amount,
                                     long tick) {
        if (amount <= 0) {
            return;
        }
        if (bucket.treasuryBacked()) {
            BannerModTreasuryLedgerSnapshot ledger = treasuryManager.getLedger(claimUuid);
            if (ledger != null) {
                treasuryManager.depositTaxes(claimUuid, ledger.anchorChunk(), ledger.settlementFactionId(), amount, tick);
            }
            return;
        }
        accountingManager.credit(claimUuid, bucket, amount, tick);
    }

    public enum DenialReason {
        NONE,
        MISSING_AUTHORITY,
        MISSING_RESOURCES,
        INVALID_STATE
    }

    public record UpgradeResult(
            boolean upgraded,
            DenialReason denialReason,
            String messageKey,
            @Nullable StrategicResourceBucket missingBucket,
            int missingAmount,
            int newLevel,
            @Nullable FortLevelDefinition newDefinition
    ) {
        private static UpgradeResult success(int newLevel, FortLevelDefinition newDefinition) {
            return new UpgradeResult(true, DenialReason.NONE, "chat.bannermod.fort.upgrade.success", null, 0, newLevel, newDefinition);
        }

        private static UpgradeResult denied(DenialReason reason, String messageKey) {
            return denied(reason, messageKey, null, 0);
        }

        private static UpgradeResult denied(DenialReason reason,
                                            String messageKey,
                                            @Nullable StrategicResourceBucket missingBucket,
                                            int missingAmount) {
            return new UpgradeResult(false, reason, messageKey, missingBucket, Math.max(0, missingAmount), 0, null);
        }
    }

    private record MissingResource(StrategicResourceBucket bucket, int missingAmount) {
    }
}
