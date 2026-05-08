package com.talhanation.bannermod.settlement.runtime;

import com.talhanation.bannermod.governance.BannerModGovernorHeartbeat;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryLedgerSnapshot;
import com.talhanation.bannermod.governance.BannerModTreasuryManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.shared.logistics.BannerModSupplyStatus;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementBinding;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

public final class SettlementTreasuryDerivationService {
    private SettlementTreasuryDerivationService() {
    }

    public static BannerModGovernorHeartbeat.BatchResult runGovernorHeartbeatBatch(ServerLevel level,
                                                                                   RecruitsClaimManager claimManager,
                                                                                   BannerModGovernorManager governorManager,
                                                                                   int startIndex,
                                                                                   int maxSnapshots) {
        BannerModTreasuryManager treasuryManager = level == null ? null : BannerModTreasuryManager.get(level);
        return BannerModGovernorHeartbeat.runGovernedClaimHeartbeatBatch(
                level,
                claimManager,
                governorManager,
                treasuryManager,
                startIndex,
                maxSnapshots
        );
    }

    @Nullable
    public static BannerModTreasuryLedgerSnapshot.FiscalRollup deriveHeartbeatAccounting(@Nullable BannerModTreasuryManager treasuryManager,
                                                                                        BannerModGovernorSnapshot snapshot,
                                                                                        BannerModSettlementBinding.Binding binding,
                                                                                        BannerModGovernorHeartbeat.HeartbeatReport report,
                                                                                        @Nullable BannerModSupplyStatus.RecruitSupplyStatus recruitSupplyStatus) {
        if (treasuryManager == null || snapshot == null || binding == null || report == null) {
            return null;
        }
        int requestedArmyUpkeepDebit = resolveRequestedArmyUpkeepDebit(recruitSupplyStatus);
        BannerModTreasuryLedgerSnapshot updated = treasuryManager.applyHeartbeatAccounting(
                snapshot.claimUuid(),
                snapshot.anchorChunk(),
                binding.claimFactionId(),
                report.taxesCollected(),
                requestedArmyUpkeepDebit,
                report.heartbeatTick()
        );
        return updated.projectFiscalRollup(report.taxesCollected(), requestedArmyUpkeepDebit, report.heartbeatTick());
    }

    private static int resolveRequestedArmyUpkeepDebit(@Nullable BannerModSupplyStatus.RecruitSupplyStatus recruitSupplyStatus) {
        if (recruitSupplyStatus == null) {
            return 0;
        }
        BannerModSupplyStatus.ArmyUpkeepStatus accounting = recruitSupplyStatus.accounting();
        if (accounting == null || !accounting.unpaid()) {
            return 0;
        }
        return accounting.unpaidLevel();
    }
}
