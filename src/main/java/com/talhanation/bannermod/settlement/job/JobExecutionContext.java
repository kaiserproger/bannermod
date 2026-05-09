package com.talhanation.bannermod.settlement.job;

import com.talhanation.bannermod.settlement.SettlementResidentRecord;
import com.talhanation.bannermod.settlement.workorder.SettlementWorkOrderRuntime;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable context passed to a {@link JobHandler} for a single decision/step.
 *
 * <p>Intentionally record-shaped. The registry layer stays server-authoritative and does
 * <em>not</em> couple to a live entity reference; later slices may add projections (e.g. a
 * snapshot of position / inventory) but the contract starts with identifiers only.</p>
 *
 * <p>The optional {@link SettlementWorkOrderRuntime} gives handlers access to the per-level
 * work-order registry so they can claim and release concrete {@code SettlementWorkOrder}
 * entries. It may be {@code null} under test conditions.</p>
 */
public record JobExecutionContext(
        SettlementResidentRecord resident,
        long gameTime,
        @Nullable UUID boundEntityUuid,
        @Nullable UUID workplaceUuid,
        @Nullable SettlementWorkOrderRuntime workOrderRuntime
) {
    public JobExecutionContext {
        Objects.requireNonNull(resident, "resident");
    }

    /** Backward-compatible constructor for tests that do not exercise the work-order layer. */
    public JobExecutionContext(SettlementResidentRecord resident,
                               long gameTime,
                               @Nullable UUID boundEntityUuid,
                               @Nullable UUID workplaceUuid) {
        this(resident, gameTime, boundEntityUuid, workplaceUuid, null);
    }

    public Optional<UUID> boundEntity() {
        return Optional.ofNullable(boundEntityUuid);
    }

    public Optional<UUID> workplace() {
        return Optional.ofNullable(workplaceUuid);
    }

    public Optional<SettlementWorkOrderRuntime> workOrders() {
        return Optional.ofNullable(workOrderRuntime);
    }
}
