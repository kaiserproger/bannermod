package com.talhanation.bannermod.settlement.dispatch;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory coordinator for live seller dispatches. Takes the persisted
 * {@link com.talhanation.bannermod.settlement.SettlementSellerDispatchState#READY}
 * seed and drives a per-seller phase machine through MOVING_TO_STALL ->
 * AT_STALL -> SELLING -> RETURNING -> RETURNED. Phase advances can be driven
 * explicitly via {@link #advance(UUID, SellerPhase, long)} or implicitly via
 * {@link #tickPhase(UUID, long)} using tick budgets documented below.
 *
 * <p>Determinism: the backing map is a {@link LinkedHashMap} so iteration order
 * matches insertion order — callers (e.g. {@link BannerModSellerDispatchAdvisor})
 * rely on that stability.
 *
 */
public final class BannerModSellerDispatchRuntime {

    /**
     * Max ticks a seller spends transitioning from READY origin to the stall
     * before auto-advance promotes them to AT_STALL. Kept short so tests can
     * cover the full path without lengthy loops.
     */
    public static final int MOVE_TO_STALL_MAX_TICKS = 40;

    /** Ticks spent settling at the stall before SELLING begins. */
    public static final int AT_STALL_DELAY_TICKS = 20;

    /** Ticks the SELLING phase may last before auto-advance to RETURNING. */
    public static final int SELLING_MAX_TICKS = 200;

    /** Ticks allowed for RETURNING before the seller is parked RETURNED. */
    public static final int RETURNING_MAX_TICKS = 60;

    /**
     * Phases that count as "not active" — the seller is either parked or was
     * never dispatched. {@link #beginDispatch} is only allowed when the seller
     * is absent from the map OR currently in one of these phases.
     */
    private static final Set<SellerPhase> IDLE_PHASES = Collections.unmodifiableSet(
            EnumSet.of(SellerPhase.READY, SellerPhase.RETURNED, SellerPhase.CANCELLED, SellerPhase.MARKET_CLOSED)
    );

    private final Map<UUID, SellerPhaseRecord> phases = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    /**
     * Begin a new dispatch for {@code sellerResidentUuid} at the market identified
     * by {@code marketRecordUuid}. The seller enters {@link SellerPhase#MOVING_TO_STALL}
     * immediately.
     *
     * @throws IllegalStateException if the seller currently holds an active
     *                               (non-idle) phase. See {@link #IDLE_PHASES}.
     */
    public void beginDispatch(UUID sellerResidentUuid, UUID marketRecordUuid, long gameTime) {
        if (sellerResidentUuid == null) {
            throw new IllegalArgumentException("sellerResidentUuid must not be null");
        }
        if (marketRecordUuid == null) {
            throw new IllegalArgumentException("marketRecordUuid must not be null");
        }
        SellerPhaseRecord existing = this.phases.get(sellerResidentUuid);
        if (existing != null && !IDLE_PHASES.contains(existing.phase())) {
            throw new IllegalStateException("seller " + sellerResidentUuid + " already active in phase " + existing.phase());
        }
        this.phases.put(
                sellerResidentUuid,
                new SellerPhaseRecord(sellerResidentUuid, marketRecordUuid, SellerPhase.MOVING_TO_STALL, gameTime, 0)
        );
        markDirty();
    }

    /**
     * Force the seller into {@code nextPhase}. Silently no-ops for unknown
     * sellers. Resets the phase tick counter and anchors {@link SellerPhaseRecord#phaseStartGameTime()}
     * to {@code gameTime}.
     */
    public void advance(UUID sellerResidentUuid, SellerPhase nextPhase, long gameTime) {
        if (sellerResidentUuid == null || nextPhase == null) {
            return;
        }
        SellerPhaseRecord current = this.phases.get(sellerResidentUuid);
        if (current == null) {
            return;
        }
        SellerPhaseRecord next = current.transitionedTo(nextPhase, gameTime);
        if (next.equals(current)) {
            return;
        }
        this.phases.put(sellerResidentUuid, next);
        markDirty();
    }

    /**
     * Advance a seller's phase by one tick. If the current phase has exceeded
     * its configured budget the next canonical phase is entered. Deterministic:
     * MOVING_TO_STALL -> AT_STALL -> SELLING -> RETURNING -> RETURNED. Terminal
     * phases (RETURNED, MARKET_CLOSED, CANCELLED, READY) are untouched.
     *
     * @return the new record after ticking, or {@link Optional#empty()} for unknown sellers
     */
    public Optional<SellerPhaseRecord> tickPhase(UUID sellerResidentUuid, long gameTime) {
        if (sellerResidentUuid == null) {
            return Optional.empty();
        }
        SellerPhaseRecord current = this.phases.get(sellerResidentUuid);
        if (current == null) {
            return Optional.empty();
        }
        SellerPhaseRecord next = computeNext(current, gameTime);
        if (next.equals(current)) {
            return Optional.of(current);
        }
        this.phases.put(sellerResidentUuid, next);
        markDirty();
        return Optional.of(next);
    }

    /**
     * Force every seller currently dispatched to {@code marketRecordUuid} into
     * {@link SellerPhase#MARKET_CLOSED}, anchored at {@code gameTime}. No-op
     * for unknown markets.
     */
    public void forceMarketClose(UUID marketRecordUuid, long gameTime) {
        if (marketRecordUuid == null) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<UUID, SellerPhaseRecord> entry : this.phases.entrySet()) {
            SellerPhaseRecord record = entry.getValue();
            if (marketRecordUuid.equals(record.marketRecordUuid())
                    && record.phase() != SellerPhase.MARKET_CLOSED) {
                entry.setValue(record.transitionedTo(SellerPhase.MARKET_CLOSED, gameTime));
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    /** Look up the current phase record for {@code sellerResidentUuid}. */
    public Optional<SellerPhaseRecord> phase(UUID sellerResidentUuid) {
        if (sellerResidentUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.phases.get(sellerResidentUuid));
    }

    /** True if the seller is currently in a non-idle (in-flight) phase. */
    public boolean isActive(UUID sellerResidentUuid) {
        SellerPhaseRecord record = sellerResidentUuid == null ? null : this.phases.get(sellerResidentUuid);
        return record != null && !IDLE_PHASES.contains(record.phase());
    }

    /**
     * Snapshot of every tracked dispatch in insertion order. Pass
     * {@code phaseFilter == null} to return all; otherwise only records whose
     * phase matches are returned.
     */
    public List<SellerPhaseRecord> activeDispatches() {
        return this.activeDispatches(null);
    }

    /**
     * Snapshot of tracked dispatches optionally filtered by {@code phaseFilter}.
     * A {@code null} filter returns every dispatch (including terminal ones).
     */
    public List<SellerPhaseRecord> activeDispatches(@Nullable SellerPhase phaseFilter) {
        List<SellerPhaseRecord> out = new ArrayList<>(this.phases.size());
        for (SellerPhaseRecord record : this.phases.values()) {
            if (phaseFilter == null || record.phase() == phaseFilter) {
                out.add(record);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag dispatches = new ListTag();
        for (SellerPhaseRecord record : activeDispatches()) {
            dispatches.add(record.toTag());
        }
        tag.put("Dispatches", dispatches);
        return tag;
    }

    public static BannerModSellerDispatchRuntime fromTag(CompoundTag tag) {
        BannerModSellerDispatchRuntime runtime = new BannerModSellerDispatchRuntime();
        List<SellerPhaseRecord> dispatches = new ArrayList<>();
        for (Tag entry : tag.getList("Dispatches", Tag.TAG_COMPOUND)) {
            dispatches.add(SellerPhaseRecord.fromTag((CompoundTag) entry));
        }
        runtime.restoreSnapshot(dispatches);
        return runtime;
    }

    public void restoreSnapshot(Collection<SellerPhaseRecord> dispatches) {
        List<SellerPhaseRecord> before = activeDispatches();
        this.phases.clear();
        if (dispatches != null) {
            for (SellerPhaseRecord dispatch : dispatches) {
                if (dispatch != null) {
                    this.phases.put(dispatch.sellerResidentUuid(), dispatch);
                }
            }
        }
        if (!before.equals(activeDispatches())) {
            markDirty();
        }
    }

    /** Drop all in-memory dispatch state. Intended for test isolation. */
    public void reset() {
        if (!this.phases.isEmpty()) {
            this.phases.clear();
            markDirty();
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static SellerPhaseRecord computeNext(SellerPhaseRecord current, long gameTime) {
        SellerPhaseRecord bumped = current.withIncrementedTick();
        switch (current.phase()) {
            case MOVING_TO_STALL -> {
                if (bumped.phaseTickCount() >= MOVE_TO_STALL_MAX_TICKS) {
                    return current.transitionedTo(SellerPhase.AT_STALL, gameTime);
                }
                return bumped;
            }
            case AT_STALL -> {
                if (bumped.phaseTickCount() >= AT_STALL_DELAY_TICKS) {
                    return current.transitionedTo(SellerPhase.SELLING, gameTime);
                }
                return bumped;
            }
            case SELLING -> {
                if (bumped.phaseTickCount() >= SELLING_MAX_TICKS) {
                    return current.transitionedTo(SellerPhase.RETURNING, gameTime);
                }
                return bumped;
            }
            case RETURNING -> {
                if (bumped.phaseTickCount() >= RETURNING_MAX_TICKS) {
                    return current.transitionedTo(SellerPhase.RETURNED, gameTime);
                }
                return bumped;
            }
            default -> {
                // Terminal or inert phases are parked until a new dispatch starts.
                return current;
            }
        }
    }

    private void markDirty() {
        this.dirtyListener.run();
    }
}
