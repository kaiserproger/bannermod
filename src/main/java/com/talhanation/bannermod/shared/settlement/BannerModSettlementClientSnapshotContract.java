package com.talhanation.bannermod.shared.settlement;

import com.talhanation.bannermod.governance.BannerModGovernorSnapshot;
import com.talhanation.bannermod.settlement.SettlementSnapshot;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable server-to-client contract for settlement/governor client mirrors.
 *
 * <p>The server is authoritative. Clients may display the last ready payload while it is
 * stale, but any gameplay mutation must request a fresh server operation instead of trusting
 * cached values.</p>
 */
public final class BannerModSettlementClientSnapshotContract {
    public static final int CURRENT_VERSION = 1;
    public static final long NO_STALE_MARKER = -1L;

    private BannerModSettlementClientSnapshotContract() {
    }

    public enum RefreshTrigger {
        /** Sent after login so client mirrors can clear loading state before a settlement screen opens. */
        LOGIN,
        /** Sent when the governor/settlement screen opens or explicitly requests a refresh. */
        SCREEN_OPEN,
        /** Sent after server-side settlement, governor, worker, claim, storage, or policy mutations. */
        MUTATION_REFRESH
    }

    public enum SnapshotState {
        /** Client has requested data but has no usable payload for the claim yet. */
        LOADING,
        /** Server answered that no settlement/governor payload exists for the requested context. */
        EMPTY,
        /** Payload matches the server version included in this envelope. */
        READY,
        /** Payload may be rendered read-only while the client asks the server for a newer version. */
        STALE
    }

    public record Payload(
            UUID claimUuid,
            @Nullable SettlementSnapshot settlementSnapshot,
            @Nullable BannerModGovernorSnapshot governorSnapshot
    ) {
        public Payload {
            Objects.requireNonNull(claimUuid, "claimUuid");
        }
    }

    public record Envelope(
            int contractVersion,
            long serverVersion,
            long createdAtGameTime,
            RefreshTrigger trigger,
            SnapshotState state,
            long staleSinceServerVersion,
            @Nullable Payload payload
    ) {
        public Envelope {
            Objects.requireNonNull(trigger, "trigger");
            Objects.requireNonNull(state, "state");
            if ((state == SnapshotState.LOADING || state == SnapshotState.EMPTY) && payload != null) {
                throw new IllegalArgumentException("loading or empty snapshots must not carry payload");
            }
            if (state != SnapshotState.LOADING && state != SnapshotState.EMPTY && payload == null) {
                throw new IllegalArgumentException("ready or stale snapshots require payload");
            }
        }

        public static Envelope loading(long createdAtGameTime, RefreshTrigger trigger) {
            return new Envelope(CURRENT_VERSION, 0L, createdAtGameTime, trigger, SnapshotState.LOADING, NO_STALE_MARKER, null);
        }

        public static Envelope empty(long serverVersion, long createdAtGameTime, RefreshTrigger trigger) {
            return new Envelope(CURRENT_VERSION, serverVersion, createdAtGameTime, trigger, SnapshotState.EMPTY, NO_STALE_MARKER, null);
        }

        public static Envelope ready(long serverVersion, long createdAtGameTime, RefreshTrigger trigger, Payload payload) {
            return new Envelope(CURRENT_VERSION, serverVersion, createdAtGameTime, trigger, SnapshotState.READY, NO_STALE_MARKER, payload);
        }

        public static Envelope stale(long serverVersion, long createdAtGameTime, RefreshTrigger trigger, long staleSinceServerVersion, Payload payload) {
            return new Envelope(CURRENT_VERSION, serverVersion, createdAtGameTime, trigger, SnapshotState.STALE, staleSinceServerVersion, payload);
        }

        public boolean isCurrentContract() {
            return this.contractVersion == CURRENT_VERSION;
        }

        public boolean isStale() {
            return this.state == SnapshotState.STALE || this.staleSinceServerVersion != NO_STALE_MARKER;
        }
    }
}
