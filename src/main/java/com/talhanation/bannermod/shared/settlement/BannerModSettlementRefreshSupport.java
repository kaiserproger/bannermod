package com.talhanation.bannermod.shared.settlement;

import com.talhanation.bannermod.events.ClaimEvents;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.governance.BannerModGovernorManager;
import com.talhanation.bannermod.persistence.military.RecruitsClaim;
import com.talhanation.bannermod.settlement.BannerModSettlementManager;
import com.talhanation.bannermod.settlement.BannerModSettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

public final class BannerModSettlementRefreshSupport {
    private static final java.util.concurrent.atomic.AtomicLong INVOCATIONS =
            new java.util.concurrent.atomic.AtomicLong();

    private BannerModSettlementRefreshSupport() {
    }

    public static void refreshSnapshot(ServerLevel level, @Nullable BlockPos pos) {
        if (level == null || pos == null || ClaimEvents.claimManager() == null) {
            return;
        }
        INVOCATIONS.incrementAndGet();
        RecruitsClaim claim = ClaimEvents.claimManager().getClaim(new net.minecraft.world.level.ChunkPos(pos));
        BannerModSettlementService.refreshClaimAt(
                level,
                ClaimEvents.claimManager(),
                BannerModSettlementManager.get(level),
                BannerModGovernorManager.get(level),
                pos
        );
        if (claim != null) {
            RecruitEvents.syncGovernorMutationRefresh(level, claim);
        }
    }

    /**
     * Test observability seam — total number of {@link #refreshSnapshot} calls that passed
     * the null-guards and reached the underlying service. GameTests use this to assert that
     * the {@code SettlementMutationRefreshEvents} subscriber actually invoked the refresh
     * pipeline in response to a civil mutation. Production code does not consult this value.
     */
    public static long invocationCount() {
        return INVOCATIONS.get();
    }

    public static void refreshTransition(ServerLevel level,
                                         @Nullable BlockPos firstPos,
                                         @Nullable BlockPos secondPos) {
        refreshSnapshot(level, firstPos);
        if (firstPos == null || secondPos == null || !firstPos.equals(secondPos)) {
            refreshSnapshot(level, secondPos);
        }
    }
}
