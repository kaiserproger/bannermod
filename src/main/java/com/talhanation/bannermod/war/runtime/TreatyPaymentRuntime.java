package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.settlement.economy.StrategicResourceAccountSnapshot;
import com.talhanation.bannermod.settlement.economy.StrategicResourceAccountingManager;
import com.talhanation.bannermod.settlement.economy.StrategicResourceBucket;
import com.talhanation.bannermod.war.audit.WarAuditLogSavedData;

import javax.annotation.Nullable;
import java.util.UUID;

public final class TreatyPaymentRuntime {
    private final TreatyRuntime treaties;
    @Nullable
    private final BannerModTreasuryManager treasury;
    @Nullable
    private final StrategicResourceAccountingManager resources;
    @Nullable
    private final RecruitsClaimManager claimManager;
    private final WarAuditLogSavedData audit;

    public TreatyPaymentRuntime(TreatyRuntime treaties,
                                @Nullable BannerModTreasuryManager treasury,
                                @Nullable StrategicResourceAccountingManager resources,
                                @Nullable RecruitsClaimManager claimManager,
                                WarAuditLogSavedData audit) {
        this.treaties = treaties;
        this.treasury = treasury;
        this.resources = resources;
        this.claimManager = claimManager;
        this.audit = audit;
    }

    public void processDue(long currentTick) {
        for (TributeTreatyRecord treaty : treaties.tributeTreaties()) {
            if (!treaty.active() || treaty.amount() <= 0 || treaty.intervalTicks() <= 0L) continue;
            if (currentTick - treaty.lastPaidAtGameTime() < treaty.intervalTicks()) continue;
            processOne(treaty, currentTick);
        }
    }

    private void processOne(TributeTreatyRecord treaty, long currentTick) {
        int paid = treaty.resourceBucket().treasuryBacked()
                ? transferCoins(treaty, currentTick)
                : transferResource(treaty, currentTick);
        int defaulted = treaty.amount() - paid;
        treaties.recordPayment(treaty.id(), treaty.lastPaidAtGameTime() + treaty.intervalTicks());
        if (paid > 0) {
            audit.append(treaty.sourceWarId(), "TREATY_TRIBUTE_PAID",
                    "treatyId=" + treaty.id()
                            + ";payer=" + treaty.payerEntityId()
                            + ";receiver=" + treaty.receiverEntityId()
                            + ";resource=" + treaty.resourceBucket().id()
                            + ";requested=" + treaty.amount()
                            + ";paid=" + paid,
                    currentTick);
        }
        if (defaulted > 0) {
            TreatyDefaultFact fact = treaties.recordDefault(treaty, treaty.amount(), paid, defaulted, currentTick);
            audit.append(treaty.sourceWarId(), "TREATY_DEFAULT",
                    "factId=" + fact.id()
                            + ";treatyId=" + treaty.id()
                            + ";payer=" + treaty.payerEntityId()
                            + ";receiver=" + treaty.receiverEntityId()
                            + ";resource=" + treaty.resourceBucket().id()
                            + ";requested=" + treaty.amount()
                            + ";paid=" + paid
                            + ";defaulted=" + defaulted,
                    currentTick);
        }
    }

    private int transferCoins(TributeTreatyRecord treaty, long currentTick) {
        if (treasury == null || claimManager == null) return 0;
        RecruitsClaim receiverClaim = firstClaimOwnedBy(treaty.receiverEntityId());
        if (receiverClaim == null) return 0;
        int remaining = treaty.amount();
        int transferred = 0;
        for (RecruitsClaim claim : claimManager.getAllClaims()) {
            if (remaining <= 0) break;
            if (!treaty.payerEntityId().equals(claim.getOwnerPoliticalEntityId())) continue;
            BannerModTreasuryLedgerSnapshot ledger = treasury.getLedger(claim.getUUID());
            if (ledger == null || ledger.treasuryBalance() <= 0) continue;
            int debit = Math.min(ledger.treasuryBalance(), remaining);
            treasury.recordArmyUpkeepDebit(claim.getUUID(), claim.getCenter(), null, debit, currentTick);
            remaining -= debit;
            transferred += debit;
        }
        if (transferred > 0) {
            treasury.depositTaxes(receiverClaim.getUUID(), receiverClaim.getCenter(), null, transferred, currentTick);
        }
        return transferred;
    }

    private int transferResource(TributeTreatyRecord treaty, long currentTick) {
        if (resources == null || claimManager == null) return 0;
        RecruitsClaim receiverClaim = firstClaimOwnedBy(treaty.receiverEntityId());
        if (receiverClaim == null) return 0;
        StrategicResourceBucket bucket = treaty.resourceBucket();
        int remaining = treaty.amount();
        int transferred = 0;
        for (RecruitsClaim claim : claimManager.getAllClaims()) {
            if (remaining <= 0) break;
            if (!treaty.payerEntityId().equals(claim.getOwnerPoliticalEntityId())) continue;
            StrategicResourceAccountSnapshot account = resources.getAccount(claim.getUUID());
            if (account == null || account.balance(bucket) <= 0) continue;
            int debit = Math.min(account.balance(bucket), remaining);
            resources.debit(claim.getUUID(), bucket, debit, currentTick);
            remaining -= debit;
            transferred += debit;
        }
        if (transferred > 0) {
            resources.credit(receiverClaim.getUUID(), bucket, transferred, currentTick);
        }
        return transferred;
    }

    @Nullable
    private RecruitsClaim firstClaimOwnedBy(UUID politicalEntityId) {
        if (claimManager == null || politicalEntityId == null) return null;
        for (RecruitsClaim claim : claimManager.getAllClaims()) {
            if (politicalEntityId.equals(claim.getOwnerPoliticalEntityId())) {
                return claim;
            }
        }
        return null;
    }
}
