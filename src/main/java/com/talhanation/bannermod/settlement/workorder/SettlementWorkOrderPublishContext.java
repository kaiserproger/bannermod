package com.talhanation.bannermod.settlement.workorder;

import com.talhanation.bannermod.settlement.SettlementBuildingRecord;
import com.talhanation.bannermod.settlement.SettlementSnapshot;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Bundle passed to {@link SettlementWorkOrderPublisher#publish} each tick.
 *
 * <p>The {@code level} may be {@code null} under test conditions where the real world is not
 * available. Publishers that depend on the world must handle that case gracefully.</p>
 */
public record SettlementWorkOrderPublishContext(
        SettlementWorkOrderRuntime runtime,
        UUID claimUuid,
        SettlementBuildingRecord building,
        SettlementSnapshot snapshot,
        @Nullable ServerLevel level,
        long gameTime
) {
    public SettlementWorkOrderPublishContext {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(claimUuid, "claimUuid");
        Objects.requireNonNull(building, "building");
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
