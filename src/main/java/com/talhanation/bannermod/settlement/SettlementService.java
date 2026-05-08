package com.talhanation.bannermod.settlement;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.persistence.military.RecruitsClaimManager;
import com.talhanation.bannermod.settlement.building.ValidatedBuildingRecord;
import com.talhanation.bannermod.settlement.runtime.SettlementClaimBindingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SettlementService {
    private SettlementService() {
    }

    public static void refreshAllClaims(ServerLevel level,
                                        RecruitsClaimManager claimManager,
                                        SettlementManager settlementManager,
                                        BannerModGovernorManager governorManager) {
        SettlementClaimBindingService.refreshAllClaims(level, claimManager, settlementManager, governorManager);
    }

    public static SettlementClaimBindingService.BatchResult refreshClaimsBatch(ServerLevel level,
                                                                               RecruitsClaimManager claimManager,
                                                                               SettlementManager settlementManager,
                                                                               BannerModGovernorManager governorManager,
                                                                               int startIndex,
                                                                               int maxClaims) {
        return SettlementClaimBindingService.refreshClaimsBatch(level, claimManager, settlementManager, governorManager, startIndex, maxClaims);
    }

    public static void refreshClaimAt(ServerLevel level,
                                      RecruitsClaimManager claimManager,
                                      SettlementManager settlementManager,
                                      BannerModGovernorManager governorManager,
                                      BlockPos pos) {
        SettlementClaimBindingService.refreshClaimAt(level, claimManager, settlementManager, governorManager, pos);
    }

    public static void refreshClaim(ServerLevel level,
                                    RecruitsClaimManager claimManager,
                                    SettlementManager settlementManager,
                                    @Nullable BannerModGovernorManager governorManager,
                                    @Nullable RecruitsClaim claim) {
        SettlementClaimBindingService.refreshClaim(level, claimManager, settlementManager, governorManager, claim);
    }

    public static SettlementSnapshot buildSnapshot(ServerLevel level,
                                                            RecruitsClaim claim,
                                                            @Nullable BannerModGovernorManager governorManager) {
        return SettlementSnapshotBuilder.buildSnapshot(level, claim, governorManager);
    }

    public static List<AbstractWorkerEntity> workersInClaim(ServerLevel level, RecruitsClaim claim) {
        return SettlementSnapshotRuntime.workersInClaim(level, claim);
    }

    public static Map<UUID, UUID> buildCanonicalWorkAreaBindings(Collection<ValidatedBuildingRecord> validatedBuildings,
                                                                 List<AbstractWorkAreaEntity> workAreas) {
        return SettlementSnapshotRuntime.buildCanonicalWorkAreaBindings(validatedBuildings, workAreas);
    }

    public static AABB claimBounds(ServerLevel level, RecruitsClaim claim) {
        return SettlementSnapshotRuntime.claimBounds(level, claim);
    }
}
